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

package tailor.icfg.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.DirectedGraph;

import tailor.icfg.BiDiICFG;

public class ICFGControlFlowGraph implements DirectedGraph<Unit> {

	private BiDiICFG<Unit, SootMethod> icfg;
	private SootMethod m;
	
	public ICFGControlFlowGraph(BiDiICFG<Unit, SootMethod> icfg, SootMethod m) {
		this.icfg = icfg;
		this.m = m;
	}
	
	@Override
	public List<Unit> getHeads() {
		return new ArrayList<>(icfg.getStartPointsOf(m));
	}

	@Override
	public List<Unit> getTails() {
		return new ArrayList<>(icfg.getEndPointsOf(m));
	}

	@Override
	public List<Unit> getPredsOf(Unit s) {
		return icfg.getPredsOf(s);
	}

	@Override
	public List<Unit> getSuccsOf(Unit s) {
		return icfg.getSuccsOf(s);
	}

	@Override
	public int size() {
		return m.getActiveBody().getUnits().size();
		//throw new UnsupportedOperationException("ICFGControlFlowGraph: size");
	}

	@Override
	public Iterator<Unit> iterator() {
		return m.getActiveBody().getUnits().iterator();
		//throw new UnsupportedOperationException("ICFGControlFlowGraph: iterator");
	}
}
