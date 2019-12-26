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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import soot.SootMethod;
import soot.Unit;
import tailor.icfg.BlockedJimpleBasedICFG;

public class InterCycleFinder {
	
	public Map<Unit, Set<SootMethod>> find(BlockedJimpleBasedICFG icfg,
			SootMethod head) {
		Map<Unit, Set<SootMethod>> interCycles = new HashMap<>();
		ICFGStronglyConnectedComponents<Unit, SootMethod> scc = 
				new ICFGStronglyConnectedComponents<>(icfg, head);
		while (scc.getTrueComponents().size() > 0) {
			Map<Unit, SootMethod> backEdges = findBackEdges(icfg, head, scc);
			backEdges.forEach((call, callee) -> {
				if (!interCycles.containsKey(call)) {
					interCycles.put(call, new HashSet<>());
				}
				interCycles.get(call).add(callee);
			});
			icfg.addInterCyclesToBeBlocked(interCycles);
			
			scc = new ICFGStronglyConnectedComponents<>(icfg, head);
		}
		return interCycles;
	}

	protected Map<Unit, SootMethod> findBackEdges(BlockedJimpleBasedICFG icfg,
			SootMethod head,
			ICFGStronglyConnectedComponents<Unit, SootMethod> scc) {
		Stack<SootMethod> stack = new Stack<>();
		Set<SootMethod> visited = new HashSet<>();
		Map<Unit, SootMethod> backEdges = new HashMap<>();
		stack.push(head);
		while (!stack.isEmpty()) {
			SootMethod m = stack.pop();
			visited.add(m);
			Set<SootMethod> component = scc.trueComponentContains(m);
			if (component != null) {
				icfg.getCallersOf(m).forEach(call -> {
					if (component.contains(icfg.getMethodOf(call))) {
						backEdges.put(call, m);
					}
				});
				break;
			} else {
				icfg.getCallsFromWithin(m).forEach(call -> {
					icfg.getCalleesOfCallAt(call).forEach(callee -> {
						if (!visited.contains(callee)) {
							stack.push(callee);
						}
					});
				});
			}
		}
		return backEdges;
	}
	
	public static void printMap(Map<?, ?> m) {
		System.out.println("Map size: " + m.size());
		m.forEach((k, v) -> {
			System.out.println(k);
			if (v instanceof Collection) {
				System.out.println(((Collection<?>)v).size());
			}
			System.out.println(v);
			System.out.println();
		});
	}
}
