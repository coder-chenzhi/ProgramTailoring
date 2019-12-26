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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.DirectedGraph;

import tailor.icfg.BiDiICFG;

public class ICFGCallGraph implements DirectedGraph<SootMethod> {

	private BiDiICFG<Unit, SootMethod> icfg;
	private SootMethod head;
	Map<SootMethod, List<SootMethod>> preds = new HashMap<>();
	Map<SootMethod, List<SootMethod>> succs = new HashMap<>();
	
	public ICFGCallGraph(BiDiICFG<Unit, SootMethod> icfg, SootMethod head) {
		this.icfg = icfg;
		this.head = head;
	}
	
	@Override
	public List<SootMethod> getHeads() {
		return Collections.singletonList(head);
	}

	@Override
	public List<SootMethod> getTails() {
		throw new UnsupportedOperationException("ICFGCallGraph: getTails");
	}
	
	@Override
	public List<SootMethod> getPredsOf(SootMethod m) {
		if (!preds.containsKey(m)) {
			preds.put(m, icfg.getCallersOf(m)
				.stream()
				.map(icfg::getMethodOf)
				.distinct()
				.collect(Collectors.toList()));
		}
		return preds.get(m);
	}

	@Override
	public List<SootMethod> getSuccsOf(SootMethod m) {
		if (!succs.containsKey(m)) {
			Set<SootMethod> s = new HashSet<>();
			icfg.getCallsFromWithin(m)
				.stream()
				.map(icfg::getCalleesOfCallAt)
				.forEach(s::addAll);
			succs.put(m, new ArrayList<>(s));
		}
		return succs.get(m);
	}

	@Override
	public int size() {
		return icfg.allMethods().size();
	}

	@Override
	public Iterator<SootMethod> iterator() {
		return icfg.allMethods().iterator();
	}

}
