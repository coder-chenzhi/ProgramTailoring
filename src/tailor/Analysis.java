/* Tailor - Program Tailoring: Slicing by Sequential Criteria
 *
 * Copyright (C) 2016 Yue Li, Tian Tan, Jingling Xue
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package tailor;

import heros.solver.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.JimpleIFDSSolver;
import tailor.extension.ExtensionFinder;
import tailor.extension.IFDSExtensionBottomUpTailor;
import tailor.extension.IFDSExtensionTopDownTailor;
import tailor.extension.tagger.BranchTagger;
import tailor.extension.tagger.ConstructorCallTagger;
import tailor.extension.tagger.CycleTagger;
import tailor.extension.tagger.ICFGTagger;
import tailor.extension.tagger.MethodTagger;
import tailor.icfg.BackwardsICFG;
import tailor.icfg.BiDiICFG;
import tailor.icfg.BlockedJimpleBasedICFG;
import tailor.icfg.BottomUpBlockedJimpleICFG;
import tailor.icfg.ICFG;
import tailor.icfg.JimpleBasedICFG;
import tailor.icfg.TopDownBlockedJimpleICFG;
import tailor.icfg.util.InterCycleFinder;
import tailor.icfg.util.IntraCycleFinder;
import tailor.icfg.util.NoExceptionGraph;
import tailor.ifds.IFDSBottomUpTailor;
import tailor.ifds.IFDSResultMap;
import tailor.ifds.IFDSTopDownTailor;
import tailor.ifds.StatementSequence;
import tailor.ifds.StatementSequenceUtil;
import tailor.tag.BottomUpReachableTag;

public class Analysis {
	
	private static final String DELIM = ";";
	
	private Set<StatementSequence> sc;
	private Set<Unit> specUnits;
	private StatementSequenceUtil util;
	
	private BackwardsICFG bwICFG; 
	private BlockedJimpleBasedICFG fwICFG;
	private IFDSResultMap buResults;
	private JimpleIFDSSolver<StatementSequence,ICFG<Unit,SootMethod>> buSolver, tdSolver;
	private IFDSBottomUpTailor buTailor;
	private IFDSTopDownTailor tdTailor;
	
	private boolean extendSC;
	
	public Analysis(Set<StatementSequence> sc,
			boolean retainCycle,
			boolean extendSC) {
		this.sc = sc;
		Set<Unit> apiCallSet = computeApiCallSet();
		Set<Unit> headSet = computeHeadSet();
		Set<Unit> tailSet = computeTailSet();
		Map<Unit, Set<LinkedList<Unit>>> followSet = computeFollowSet();
		util = new StatementSequenceUtil(apiCallSet, headSet, tailSet, followSet);
		
		specUnits = new HashSet<>(apiCallSet);
		specUnits.removeAll(tailSet);
		
		// construct ICFG
		fwICFG = new TopDownBlockedJimpleICFG(specUnits);
		if (!retainCycle) {
			IntraCycleFinder intraFinder = new IntraCycleFinder();
			Map<Pair<Unit, Unit>, Set<Unit>> intraCycles = new HashMap<>();
			fwICFG.allMethods()
				.stream()
				//.filter(m -> m.getDeclaringClass().isApplicationClass())
				.forEach(m -> {
					if (m.hasActiveBody()) {
						Map<Pair<Unit, Unit>, Set<Unit>> cycles =
								intraFinder.find(
										new NoExceptionGraph<>(fwICFG.getOrCreateUnitGraph(m)),
										m);
						// examine intra-procedural cycles
						if (!cycles.isEmpty()) {
							System.out.println("Found back edges in " + m + " :");
							cycles.keySet().forEach(p -> {
								System.out.println("> " + p.getO1() + " -> " + p.getO2());
							});
							System.out.println();
							System.out.println("Redirections:");
							InterCycleFinder.printMap(cycles);
							System.out.println();
						}
						intraCycles.putAll(cycles);
					}
				});
			
			InterCycleFinder interFinder = new InterCycleFinder();
			Map<Unit, Set<SootMethod>> interCycles =
					interFinder.find(fwICFG, Scene.v().getMainMethod());
			InterCycleFinder.printMap(interCycles);
			
			fwICFG = new TopDownBlockedJimpleICFG(specUnits, intraCycles, interCycles);
			bwICFG = new BackwardsICFG(
						new BottomUpBlockedJimpleICFG(specUnits, intraCycles, interCycles));
			
		} else {
			bwICFG = new BackwardsICFG(
						new BottomUpBlockedJimpleICFG(specUnits));
		}
		this.extendSC = extendSC;
		if (extendSC) { // pre-analysis for selecting extension points.
			long beginTime, endTime;
			System.out.print("  Finding SC Extension (SCEXT) ... ");
			beginTime = System.currentTimeMillis();
			
			MethodTagger[] methodTaggers = {
				BranchTagger.INSTANCE,
				ConstructorCallTagger.INSTANCE,
			};
			bwICFG.allMethods().forEach(m -> {
				for (MethodTagger tagger : methodTaggers) {
					tagger.tag(m);
				}
			});
			
			ICFGTagger[] sccTaggers = { CycleTagger.INSTANCE, };
			for (ICFGTagger tagger : sccTaggers) {
				tagger.tag(fwICFG);
			}
			
			ExtensionFinder extFinder = new ExtensionFinder(fwICFG, util);
			util.setExtension(extFinder.find(sc));
			endTime = System.currentTimeMillis();
			System.out.println("elapsed time: "
					+ Driver.costTimeToString(beginTime, endTime));
		}
	}

	public static Set<StatementSequence> readSC(File scFile) {
		ICFG<Unit, SootMethod> icfg = new JimpleBasedICFG();
		UnitFinder unitFinder = new UnitFinder(icfg);
		Set<StatementSequence> facts = new LinkedHashSet<>();
		try {
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(
							new FileInputStream(scFile)));
			String line;
			_outerLoop:
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty()) {
					continue;
				}
				try {
					LinkedList<Unit> apiCallSeq = new LinkedList<Unit>();
					for (String s : line.split(DELIM)) {
						if (!s.isEmpty()) {
							Unit call = unitFinder.find(s);
							if (call == null) {
								System.out.println("Read statement sequence [" + line + "] fails.");
								continue _outerLoop;
							}
							apiCallSeq.addLast(call);
						}
					}
					facts.add(new StatementSequence(apiCallSeq));
				} catch (RuntimeException e) {
					Debug.println("Read statement sequence [" + line + "] fails.");
				}
			}
			reader.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException("SC file not found.", e);
		} catch (IOException e) {
			throw new RuntimeException("Cannot read SC", e);
		}
		return facts;
	}
	
	public Set<Unit> computeApiCallSet() {
		Set<Unit> apiCallSet = new HashSet<>();
		sc.forEach(seq ->
			seq.getStmtSeq().forEach(apiCall -> apiCallSet.add(apiCall)));
		return apiCallSet;
	}
	
	public Set<Unit> computeHeadSet() {
		Set<Unit> headSet = new HashSet<>();
		sc.forEach(seq ->
			headSet.add(seq.getHead()));
		return headSet;
	}
	
	public Set<Unit> computeTailSet() {
		Set<Unit> tailSet = new HashSet<>();
		sc.forEach(seq ->
			tailSet.add(seq.getTail()));
		return tailSet;
	} 
	
	public Map<Unit, Set<LinkedList<Unit>>> computeFollowSet() {
		Map<Unit, Set<LinkedList<Unit>>> followSet = new HashMap<>();
		sc.forEach(seq ->
			addApiCallSeqToFollowSet(followSet, seq.getStmtSeq()));
		return followSet;
	}
	
	public void addApiCallSeqToFollowSet(Map<Unit, Set<LinkedList<Unit>>> followSet,
			LinkedList<Unit> apiCallSeq) {
		if (apiCallSeq.size() < 2) {
			return;
		}
		Unit first = apiCallSeq.getFirst();
		LinkedList<Unit> follow = new LinkedList<>(apiCallSeq);
		follow.removeFirst();
		if (!followSet.containsKey(first)) {
			followSet.put(first, new HashSet<>());
		}
		followSet.get(first).add(follow);
		addApiCallSeqToFollowSet(followSet, follow);
	}
	
	public void addExtensionFollowSet(Map<Unit, Set<LinkedList<Unit>>> followSet,
			LinkedList<Unit> apiCallSeq) {
		if (util.isSCHead(apiCallSeq.getFirst())) {
			return;
		}
		Unit first = apiCallSeq.getFirst();
		LinkedList<Unit> follow = new LinkedList<>(apiCallSeq);
		follow.removeFirst();
		if (!followSet.containsKey(first)) {
			followSet.put(first, new HashSet<>());
		}
		followSet.get(first).add(follow);
		addExtensionFollowSet(followSet, follow);
	}
	
	public BackwardsICFG getBackwardICFG() { return bwICFG; }
	
	public BlockedJimpleBasedICFG getForwardICFG() { return fwICFG; }
	
	public void analyse() {	
		SootMethod mainMethod = Scene.v().getMainMethod();
		Set<Unit> bwInitialUnits = new HashSet<>(bwICFG.getStartPointsOf(mainMethod));
		
		// initial and solve bottom-up problem
		if (extendSC) {
			buTailor = new IFDSExtensionBottomUpTailor(bwICFG, util, bwInitialUnits);
		} else {
			buTailor = new IFDSBottomUpTailor(bwICFG, util, bwInitialUnits);
		}
		
		System.out.println("  SC-Based Data-Flow Analysis (SCDFA) starts ...");
		long beginTime = System.currentTimeMillis();
		
		System.out.println("    Running Bottom-Up pass ...");
		{
			buSolver = new JimpleIFDSSolver<StatementSequence,ICFG<Unit,SootMethod>>(buTailor);
			buSolver.solve();
			buResults = new IFDSResultMap(buSolver, bwICFG);
		}
		// add reachable tags
		addBottomUpReachableTag(bwICFG, buResults);
		
		Set<StatementSequence> tdInitialFacts = new HashSet<>();
		bwICFG.getEndPointsOf(mainMethod)
			.forEach(u -> tdInitialFacts.addAll(buResults.ifdsResultsAt(u)));
		if (extendSC) {
			removeUselessBottomUpFacts(tdInitialFacts);
		} else {
			tdInitialFacts.retainAll(sc);
		}
		
		System.out.println("    Running Top-Down pass ...");
		// initial and solve top-down problem
		if (extendSC) {
			tdTailor =  new IFDSExtensionTopDownTailor(fwICFG, util, tdInitialFacts);
		} else {
			tdTailor =  new IFDSTopDownTailor(fwICFG, util, tdInitialFacts);
		}
		
		tdSolver = new JimpleIFDSSolver<>(tdTailor);
		tdSolver.solve();

		long endTime = System.currentTimeMillis();
		System.out.println("  SCDFA has run for: "
				+ Driver.costTimeToString(beginTime, endTime));
//		dumpResult();
	}
	
	private void removeUselessBottomUpFacts(Set<StatementSequence> facts) {
		Set<StatementSequence> tempFacts = new HashSet<>(facts);
		// remove subsequence fact
		tempFacts.forEach(tempFact -> {
			for (StatementSequence fact : facts) {
				if (isSubFact(tempFact, fact)) {
					facts.remove(tempFact);
					break;
				}
			}
		});
		// remove facts which does not fulfill given relations
		tempFacts.forEach(tempFact -> {
			boolean shouldRemove = true;
			for (StatementSequence seq : sc) {
				if (isSubFact(seq, tempFact) || seq.equals(tempFact)) {
					shouldRemove = false;
					break;
				}
			}
			if (shouldRemove) {
				facts.remove(tempFact);
			}
		});
	}
	
	private boolean isSubFact(StatementSequence subFact, StatementSequence fact) {
		LinkedList<Unit> subSeq = subFact.getStmtSeq();
		LinkedList<Unit> seq = fact.getStmtSeq();
		if (seq.size() > subSeq.size()) {
			for (int i = 1; i <= subSeq.size(); ++i) {
				if (!subSeq.get(subSeq.size() - i).equals(seq.get(seq.size() - i))) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	public void addBottomUpReachableTag(
			BiDiICFG<Unit,SootMethod> bwICFG,
			IFDSResultMap buResults) {
		bwICFG.allNodes()
			.forEach(u -> {
				if (!buResults.ifdsResultsAt(u).isEmpty()) {
					if (!u.hasTag(BottomUpReachableTag.NAME)) {
						u.addTag(BottomUpReachableTag.INSTANCE);
					}
				}
			});
	}
	
	public Collection<Unit> getResultsOfMethod(SootMethod m) {
		Collection<Unit> res = new ArrayList<>();
		if (m.hasActiveBody()) {
			// match
			for (Unit u : m.getActiveBody().getUnits()) {
				Set<StatementSequence> buRes = buResults.ifdsResultsAt(u);
				Set<StatementSequence> tdRes = tdSolver.ifdsResultsAt(u);
				_matchLoop:
				for (StatementSequence tdFact : tdRes) {
					if (tdFact.isEpsilon()
							&& u.hasTag(BottomUpReachableTag.NAME)) {
						res.add(u);
						break _matchLoop;
					} else {
						for (StatementSequence buFact : buRes) {
							if (buFact.getStmtSeq().equals(tdFact.getStmtSeq())) {
								res.add(u);
								break _matchLoop;
							}
						}
					}
				}
			}
		}
		return res;
	}
	
	public Collection<Unit> getResultCollection() {
		if (buResults != null && tdSolver != null) {
			Collection<Unit> results = new ArrayList<>();
			fwICFG.allMethods().forEach(m -> results.addAll(getResultsOfMethod(m)));
			return results;
		} else {
			throw new RuntimeException("Solvers are not initialed, please call analyse().");
		}
	}
	
	public Map<SootMethod, Collection<Unit>> getResultMap() {
		if (buResults != null && tdSolver != null) {
			Map<SootMethod, Collection<Unit>> results = new LinkedHashMap<>();
			fwICFG.allMethods().forEach(m -> {
				Collection<Unit> res = getResultsOfMethod(m);
				if (!res.isEmpty()) {
					results.put(m, res);
				}
			});
			return results;
		} else {
			throw new RuntimeException("Solvers are not initialed, please call analyse().");
		}
	}
	
	public void queryBottomUpResultsOf(SootMethod m) {
		Debug.println("/\\ Bottom-Up results:");
		Debug.queryResultsOf(buSolver, m);
	}
	
	public void queryTopDownResultsOf(SootMethod m) {
		Debug.println("\\/ Top-Down results:");
		Debug.queryResultsOf(tdSolver, m);
	}
	
	public void dumpResult() {
		fwICFG.allMethods()
		.stream()
		.filter(m -> m.getDeclaringClass().isApplicationClass())
		.sorted((m1, m2) -> {
			if (m1.getDeclaringClass().equals(m2.getDeclaringClass())) {
				return m1.getSignature().compareTo(m2.getSignature());
			} else {
				return m1.getDeclaringClass().getName()
						.compareTo(m2.getDeclaringClass().getName());
			}
		})
		.forEach(m -> {
			queryBottomUpResultsOf(m);
			queryTopDownResultsOf(m);
		});
		
	}
	
	public void cleanTag(String tagName) {
		fwICFG.allNodes().forEach(n -> {
			while (n.hasTag(tagName)) {
				n.removeTag(tagName);
			}
		});
	}
}
