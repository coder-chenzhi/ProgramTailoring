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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import soot.G;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.Stmt;
import soot.tagkit.Tag;
import tailor.icfg.ICFG;
import tailor.icfg.JimpleBasedICFG;
import tailor.ifds.StatementSequence;
import tailor.tag.BottomUpReachableTag;

import com.google.common.io.ByteStreams;

public class Driver {
	
	static ICFG<Unit, SootMethod> icfg;
	
	private static long beginTime, endTime;
	
	public static void main(String[] args) throws Exception {
		
		System.out.println("Tailor starts.");
		
		args = Options.processArgs(args);
		
		G.v().out = new PrintStream(ByteStreams.nullOutputStream());
		
		System.out.print("Building ICFG ... ");
		beginTime = System.currentTimeMillis();
		
		PackManager.v().getPack("wjtp").add(
				new Transform("wjtp.ifds.tailor", new SceneTransformer() {

			@Override
			protected void internalTransform(String phaseName,
					Map<String, String> options) {
				
				icfg = new JimpleBasedICFG();
				endTime = System.currentTimeMillis();
				System.out.println("elapsed time: " + costTimeToString(beginTime, endTime));
				System.out.println();
				
				Debug.setDebug(true);
				if (Options.getSCFileName() == null) {
					throw new RuntimeException("None of SC file is given.");
				}
				
				// 1. Read and group SCs by tail
				System.out.println("Reading sequential criteria (SC) ...");
				File scFile = new File(Options.getSCFileName());
				Set<StatementSequence> seqs = Analysis.readSC(scFile);
				if (seqs.isEmpty()) {
					System.out.println("None of legal SC is given.");
					System.out.println();					
				}
				Map<Unit, Set<StatementSequence>> facts = groupFactsByTail(seqs);
				
				// 2. Analyze SCs group by group
				Map<Unit, Map<SootMethod, Collection<Unit>>> results = new HashMap<>();
				facts.forEach((tail, sc) -> {
					
					System.out.printf("%d statement sequence(s) in the SC:\n", sc.size());
					sc.forEach(fact -> System.out.println("> " + toSCFormat(icfg, fact)));
					System.out.println();
					
					System.out.println("Program tailoring starts.");
					Map<SootMethod, Collection<Unit>> resultMap = runAnalysis(tail, sc);
					System.out.println("Program tailoring finishes.");
					System.out.println();
					
					if (resultMap.isEmpty()) {
						System.out.println("The tailored program is empty,"
								+ " which means that the given SC should be infeasible "
								+ "(if the ICFG is sound).");
						System.out.println();
					}
					
					results.put(tail, resultMap);
					
					String outFileName = Paths
							.get(Options.getOutDir(), toFileName(tail, "all"))
							.toString();
					System.out.printf("Dumping analysis results to %s ...\n", outFileName);
					outputResult(resultMap, outFileName);
				});
				Debug.close();
				System.out.println("Tailor finishes.");
			}
		}));
		
		soot.Main.main(args);
	}
	
	public static Map<Unit, Set<StatementSequence>>
			groupFactsByTail(Set<StatementSequence> sc) {
		return sc.stream().
			collect(Collectors.groupingBy(
					StatementSequence::getTail,
					LinkedHashMap::new,
					Collectors.toSet()));
	}
	
	public static String toSimpleFormat(SootMethod m) {
		return m.getDeclaringClass().getName()
				+ "."
				+ m.getName();
	}
	
	public static String toSCFormat(Unit call, SootMethod inMethod) {
		SootMethod callee = ((Stmt) call).getInvokeExpr().getMethod();
		return toSimpleFormat(inMethod)
				+ "/"
				+ toSimpleFormat(callee)
				+ "/"
				+ call.getJavaSourceStartLineNumber();
	}
	
	public static String toSCFormat(ICFG<Unit, SootMethod> icfg,
			StatementSequence fact) {
		StringBuilder sb = new StringBuilder();
		fact.getStmtSeq().forEach(call -> {
			sb.append(toSCFormat(call, icfg.getMethodOf(call)) + ";");
		});
		return sb.toString();
	}
	
	public static Map<SootMethod, Collection<Unit>> runAnalysis(
			Unit tail,
			Set<StatementSequence> sc) {
		
		Analysis ra = new Analysis(sc,
				Options.isRetainCycle(),
				Options.isExtendSC());
		ra.analyse();
		Map<SootMethod, Collection<Unit>> resultMap = ra.getResultMap();
		ra.cleanTag(BottomUpReachableTag.NAME);
		return resultMap;
	}
	
	public static String toFileName(Unit tail, String descr) {
		SootMethod inMethod = icfg.getMethodOf(tail);
		SootMethod callee = ((Stmt) tail).getInvokeExpr().getMethod();
		return inMethod.getDeclaringClass().getShortName()
				+ "." + inMethod.getName()
				+ ";" + callee.getDeclaringClass().getShortName()
				+ "." + callee.getName()
				+ ";" + tail.getJavaSourceStartLineNumber()
				+ (Options.isExtendSC() ? "-ext" : "")
				+ "-" + descr
				+ "-" + Scene.v().getMainClass().getName()
				+ ".output";
	}
	
	public static void outputResult(Map<SootMethod, Collection<Unit>> resultMap,
			String filePath) {
		// Output results of tailor to file.
		try {
			File file = new File(filePath);
			if (!file.exists()) {
				file.createNewFile();
			}
			PrintWriter writer = new PrintWriter(file);
			resultMap.keySet()
					.stream()
					.sorted((m1, m2) -> m1.toString().compareTo(m2.toString()))
					.forEach(m -> {
						Collection<Unit> units = resultMap.get(m);
						writer.println("==========================================================");
						writer.printf("Method: %s\n", m.toString());
						writer.println("Units in the tailored program:");
						units.forEach(u ->
							writer.printf("  %s {line: %d}\n",
									u, u.getJavaSourceStartLineNumber()));
					});
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static Set<Unit> getUnitsWithTag(ICFG<Unit, SootMethod> icfg, Tag tag) {
		Set<Unit> reachableUnits = new LinkedHashSet<Unit>();
		icfg.allMethods().forEach(m -> {
			if (m.hasActiveBody()) {
				for (Unit u : m.getActiveBody().getUnits()) {
					if (u.hasTag(tag.getName())) {
						reachableUnits.add(u);
					}
				}
			}
		});
		return reachableUnits;
	}
	
	public static Set<Unit> selectApplicationUnits(Collection<Unit> units,
			ICFG<Unit, SootMethod> icfg) {
		return units.stream()
			.filter(u -> icfg.getMethodOf(u).getDeclaringClass().isApplicationClass())
			.collect(Collectors.toSet());
	}
	
	public static Set<Unit> selectUnblockedUnits(Collection<Unit> units,
			ICFG<Unit, SootMethod> icfg) {
		return units.stream()
				.filter(u -> {
					for (String prefix : Options.getBlockPackagePrefixs()) {
						String packageName = icfg
								.getMethodOf(u)
								.getDeclaringClass()
								.getPackageName();
						if (packageName.startsWith(prefix)) {
							return false;
						}
					}
					return true;
				})
				.collect(Collectors.toSet());
	}
	
	public static Set<String> linesOfUnits(Collection<Unit> units,
			ICFG<Unit, SootMethod> icfg) {
		return units.stream()
				.filter(u -> u.getJavaSourceStartLineNumber() > 0)
				.map(u -> icfg.getMethodOf(u).getDeclaringClass().toString()
						+ ":" + u.getJavaSourceStartLineNumber())
				.collect(Collectors.toSet());
	}
	
	public static String costTimeToString(long beginTime, long endTime) {
		long costTime = endTime - beginTime;
		long min = costTime / 1000 / 60;
		long sec = (costTime / 1000) % 60;
		return min + " min. " + sec + " sec.";
	}
}

