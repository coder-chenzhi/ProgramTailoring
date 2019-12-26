/* Tailor - Program Tailoring: by Sequential Criteria
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.toolkits.graph.DirectedGraph;
import tailor.icfg.JimpleBasedICFG;


/**
 * Same as {@link JimpleBasedICFG} but based on inverted unit graphs.
 * This should be used for backward analysis.
 */
public class BackwardsICFG implements BiDiICFG<Unit,SootMethod> {
	
	protected final BiDiICFG<Unit,SootMethod> delegate;
	
	public BackwardsICFG(BiDiICFG<Unit,SootMethod> fwICFG) {
		delegate = fwICFG;
	}
	
	public BiDiICFG<Unit,SootMethod> getForwardInterproceduralCFG() {
		return delegate;
	}

	//swapped
	@Override
	public List<Unit> getSuccsOf(Unit n) {
		return delegate.getPredsOf(n);
	}

	//swapped
	@Override
	public Collection<Unit> getStartPointsOf(SootMethod m) {
		return delegate.getEndPointsOf(m);
	}

	//swapped
	@Override
	public boolean isExitStmt(Unit stmt) {
		return delegate.isStartPoint(stmt);
	}

	//swapped
	@Override
	public boolean isStartPoint(Unit stmt) {
		return delegate.isExitStmt(stmt);
	}
	
	//swapped
	@Override
	public Set<Unit> allNonCallStartNodes() {
		Set<Unit> res = new LinkedHashSet<Unit>(delegate.allNodes());
		for (Iterator<Unit> iter = res.iterator(); iter.hasNext();) {
			Unit u = iter.next();
			if(delegate.isExitStmt(u) || delegate.isReturnSite(u)) iter.remove();
		}
		return res;
	}
	
	//swapped
	@Override
	public List<Unit> getPredsOf(Unit u) {
		return delegate.getSuccsOf(u);
	}

	//swapped
	@Override
	public Collection<Unit> getEndPointsOf(SootMethod m) {
		return delegate.getStartPointsOf(m);
	}

	//swapped
	@Override
	public List<Unit> getPredsOfCallAt(Unit u) {
		return delegate.getSuccsOf(u);
	}

	//swapped
	@Override
	public Set<Unit> allNonCallEndNodes() {
		Set<Unit> res = new LinkedHashSet<Unit>(delegate.allNodes());
		for (Iterator<Unit> iter = res.iterator(); iter.hasNext();) {
			Unit u = iter.next();
			if(delegate.isStartPoint(u) || delegate.isReturnSite(u)) iter.remove();
		}
		return res;
	}

	//same
	@Override
	public SootMethod getMethodOf(Unit n) {
		return delegate.getMethodOf(n);
	}
	
	//swapped
	@Override
	public List<Unit> getReturnSitesOfCallAt(Unit n) {
		return delegate.getPredsOfCallAt(n);
	}
	
	/**
	 * The following interface methods (labeled Tailor) are modified to 
	 * enable IFDSBottomUpTailor, or real backward analysis.
	 * They are also compatible with BlockedJimpleBasedICFG.
	 */
	
	//swapped
	@Override
	public Collection<SootMethod> getCalleesOfCallAt(Unit n) {
		Collection<SootMethod> callees = new HashSet<SootMethod>();
		for (Unit pred : delegate.getPredsOf(n))
			if (delegate.isCallStmt(pred))
				callees.addAll(delegate.getCalleesOfCallAt(pred));
		return callees;
	}

	//swapped
	@Override
	public Collection<Unit> getCallersOf(SootMethod m) {
		Collection<Unit> callers = new HashSet<Unit>();
		for(Unit forwardCallsite : delegate.getCallersOf(m)) 
			callers.addAll(delegate.getSuccsOf(forwardCallsite));
		return callers;
	}

	//swapped
	@Override
	public boolean isCallStmt(Unit stmt) {
		return delegate.isReturnSite(stmt);
	}
	
	//swapped
	@Override
	public boolean isReturnSite(Unit n) {
		return delegate.isCallStmt(n);
	}

	
	//swapped
	@Override
	public Set<Unit> getCallsFromWithin(SootMethod m) {
		return delegate.getReturnsFromWithin(m);
	}

	//same
	@Override
	public DirectedGraph<Unit> getOrCreateUnitGraph(SootMethod m) {
		return delegate.getOrCreateUnitGraph(m);
	}

	//same
	@Override
	public List<Value> getParameterRefs(SootMethod m) {
		return delegate.getParameterRefs(m);
	}

	@Override
	public boolean isFallThroughSuccessor(Unit stmt, Unit succ) {
		throw new UnsupportedOperationException("not implemented because semantics unclear");
	}

	@Override
	public boolean isBranchTarget(Unit stmt, Unit succ) {
		throw new UnsupportedOperationException("not implemented because semantics unclear");
	}

	//same
	@Override
	public Set<Unit> allNodes() {
		return delegate.allNodes();
	}

	//swapped
	@Override
	public Set<Unit> getReturnsFromWithin(SootMethod m) {
		return delegate.getCallsFromWithin(m);
	}

	//same
	@Override
	public Set<SootMethod> allMethods() {
		return delegate.allMethods();
	}
}
