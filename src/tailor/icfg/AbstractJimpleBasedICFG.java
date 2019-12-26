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

package tailor.icfg;

import heros.DontSynchronize;
import heros.SynchronizedBy;
import heros.solver.IDESolver;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.UnitBox;
import soot.Value;
import soot.jimple.Stmt;
import soot.toolkits.exceptions.UnitThrowAnalysis;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public abstract class AbstractJimpleBasedICFG implements BiDiICFG<Unit,SootMethod> {

	@DontSynchronize("written by single thread; read afterwards")
	protected final Map<Unit,Body> unitToOwner = new HashMap<Unit,Body>();
	
	public Set<Unit> allNodes() {
		return unitToOwner.keySet();
	}
	
	public Set<SootMethod> allMethods() {
		return unitToOwner.values()
				.stream()
				.map(b -> b.getMethod())
				.collect(Collectors.toSet());
	}
	
	public Set<Unit> getReturnsFromWithin(SootMethod m) {
		return methodToReturnsFromWithin.getUnchecked(m);
	}
		
	
	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<Body,DirectedGraph<Unit>> bodyToUnitGraph = IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<Body,DirectedGraph<Unit>>() {
					@Override
					public DirectedGraph<Unit> load(Body body) throws Exception {
						return makeGraph(body);
					}
				});
	
	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<SootMethod,List<Value>> methodToParameterRefs = IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<SootMethod,List<Value>>() {
					@Override
					public List<Value> load(SootMethod m) throws Exception {
						return m.getActiveBody().getParameterRefs();
					}
				});

	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<SootMethod,Set<Unit>> methodToCallsFromWithin = IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<SootMethod,Set<Unit>>() {
					@Override
					public Set<Unit> load(SootMethod m) throws Exception {
						Set<Unit> res = null;
						for(Unit u: m.getActiveBody().getUnits()) {
							if(isCallStmt(u)) {
								if (res == null)
									res = new LinkedHashSet<Unit>();
								res.add(u);
							}
						}
						return res == null ? Collections.<Unit>emptySet() : res;
					}
				});

	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<SootMethod,Set<Unit>> methodToReturnsFromWithin = IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<SootMethod,Set<Unit>>() {
					@Override
					public Set<Unit> load(SootMethod m) throws Exception {
						Set<Unit> res = null;
						for(Unit u: m.getActiveBody().getUnits()) {
							if(isReturnSite(u)) {
								if (res == null)
									res = new LinkedHashSet<Unit>();
								res.add(u);
							}
						}
						return res == null ? Collections.<Unit>emptySet() : res;
					}
				});
	
	@Override
	public SootMethod getMethodOf(Unit u) {
		assert unitToOwner.containsKey(u) : "Statement " + u
				+ " not in unit-to-owner mapping";
		return unitToOwner.get(u).getMethod();
	}

	@Override
	public List<Unit> getSuccsOf(Unit u) {
		Body body = unitToOwner.get(u);
		DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
		return unitGraph.getSuccsOf(u);
	}

	@Override
	public DirectedGraph<Unit> getOrCreateUnitGraph(SootMethod m) {
		return getOrCreateUnitGraph(m.getActiveBody());
	}

	public DirectedGraph<Unit> getOrCreateUnitGraph(Body body) {
		return bodyToUnitGraph.getUnchecked(body);
	}

	protected DirectedGraph<Unit> makeGraph(Body body) {
		return new ExceptionalUnitGraph(body, UnitThrowAnalysis.v() ,true);
	}

	@Override
	public boolean isExitStmt(Unit u) {
		Body body = unitToOwner.get(u);
		DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
		return unitGraph.getTails().contains(u);
	}

	@Override
	public boolean isStartPoint(Unit u) {
		Body body = unitToOwner.get(u);
		DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);		
		return unitGraph.getHeads().contains(u);
	}

	@Override
	public boolean isFallThroughSuccessor(Unit u, Unit succ) {
		assert getSuccsOf(u).contains(succ);
		if(!u.fallsThrough()) return false;
		Body body = unitToOwner.get(u);
		return body.getUnits().getSuccOf(u) == succ;
	}

	@Override
	public boolean isBranchTarget(Unit u, Unit succ) {
		assert getSuccsOf(u).contains(succ);
		if(!u.branches()) return false;
		for (UnitBox ub : u.getUnitBoxes()) {
			if(ub.getUnit()==succ) return true;
		}
		return false;
	}

	public List<Value> getParameterRefs(SootMethod m) {
		return methodToParameterRefs.getUnchecked(m);
	}

	@Override
	public Collection<Unit> getStartPointsOf(SootMethod m) {
		if(m.hasActiveBody()) {
			Body body = m.getActiveBody();
			DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
			return unitGraph.getHeads();
		}
		return Collections.emptySet();
	}

	@Override
	public boolean isCallStmt(Unit u) {
		return ((Stmt)u).containsInvokeExpr();
	}

	@Override
	public Set<Unit> allNonCallStartNodes() {
		Set<Unit> res = new LinkedHashSet<Unit>(unitToOwner.keySet());
		for (Iterator<Unit> iter = res.iterator(); iter.hasNext();) {
			Unit u = iter.next();
			if(isStartPoint(u) || isCallStmt(u)) iter.remove();
		}
		return res;
	}
	
	@Override
	public Set<Unit> allNonCallEndNodes() {
		Set<Unit> res = new LinkedHashSet<Unit>(unitToOwner.keySet());
		for (Iterator<Unit> iter = res.iterator(); iter.hasNext();) {
			Unit u = iter.next();
			if(isExitStmt(u) || isCallStmt(u)) iter.remove();
		}
		return res;
	}

	@Override
	public Collection<Unit> getReturnSitesOfCallAt(Unit u) {
		return getSuccsOf(u);
	}

	@Override
	public Set<Unit> getCallsFromWithin(SootMethod m) {
		return methodToCallsFromWithin.getUnchecked(m);		
	}
	
	@Override
	public List<Unit> getPredsOf(Unit u) {
		assert u != null;
		Body body = unitToOwner.get(u);
		DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
		return unitGraph.getPredsOf(u);
	}

	@Override
	public Collection<Unit> getEndPointsOf(SootMethod m) {
		if(m.hasActiveBody()) {
			Body body = m.getActiveBody();
			DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
			return unitGraph.getTails();
		}
		return Collections.emptySet();
	}
	
	@Override
	public List<Unit> getPredsOfCallAt(Unit u) {
		return getPredsOf(u);
	}
	
	@Override
	public boolean isReturnSite(Unit n) {
		for (Unit pred : getPredsOf(n))
			if (isCallStmt(pred))
				return true;
		return false;
	}
	
}