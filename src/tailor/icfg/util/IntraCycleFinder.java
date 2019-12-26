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

import heros.solver.Pair;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.IdentityStmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.DominatorsFinder;
import soot.toolkits.graph.MHGDominatorsFinder;

public class IntraCycleFinder {

	private DominatorsFinder<Unit> domFinder;
	private Map<Unit, Set<Unit>> loops;
	private Map<Unit, Set<Unit>> exitsOfLoops;
	
	public Map<Pair<Unit, Unit>, Set<Unit>> find(DirectedGraph<Unit> graph, SootMethod m) {
		// 1. find back edges
		Set<Pair<Unit, Unit>> backEdges = findBackEdges(graph);
		
		// 2. construct natural loops
		computeLoops(graph, backEdges);
		
		// 3. find exits of loops
		exitsOfLoops = new HashMap<>();
		loops.keySet().forEach(h -> findExitsOfLoop(h, graph));

		// 4. compute redirection
		Map<Pair<Unit, Unit>, Set<Unit>> results = new HashMap<>();
		backEdges.forEach(backEdge -> {
			Unit head = backEdge.getO2();
//			System.out.println("Loop head: " + head
//					+ " in " + m
//					+ ", loop size: " + loops.get(head).size());
//			System.out.println(loops.get(head));
			if (isCatchStmt(head)) {
				results.put(backEdge, Collections.emptySet());
			} else {
				results.put(backEdge, exitsOfLoops.get(head));
			}
		});
		return results;
	}
	
	public Set<Pair<Unit, Unit>> findBackEdges(DirectedGraph<Unit> graph) {
		domFinder = new MHGDominatorsFinder<>(graph);
		Set<Pair<Unit, Unit>> backEdges = new HashSet<>();
		graph.forEach(n -> {
			List<Unit> doms = domFinder.getDominators(n);
			List<Unit> backEdgePreds = doms
					.stream()
					.filter(d -> graph.getSuccsOf(n).contains(d))
					.collect(Collectors.toList());
			backEdgePreds.forEach(d -> backEdges.add(new Pair<>(n, d)));
		});
		return backEdges;
	}
	
	public Map<Unit, Set<Unit>> computeLoops(DirectedGraph<Unit> graph,
			Set<Pair<Unit, Unit>> backEdges) {
		loops = new HashMap<>();
		backEdges.forEach(backEdge -> {
			Unit header = backEdge.getO2();
			if (!isCatchStmt(header)) {
				if (!loops.containsKey(header)) {
					loops.put(header, new HashSet<>());
				}
				loops.get(header).addAll(constructNaturalLoop(backEdge, graph));
			}
		});
		return loops;
	}

	protected Set<Unit> constructNaturalLoop(Pair<Unit, Unit> backEdge, DirectedGraph<Unit> graph) {
		Unit source = backEdge.getO1();
		Unit header = backEdge.getO2();
		// target is the header of the loop
		Set<Unit> visited = new HashSet<>();
		visited.add(source);
		visited.add(header);
		Queue<Unit> q = new ArrayDeque<>();
		q.add(source);
		while (!q.isEmpty()) {
			Unit n = q.poll();
			graph.getPredsOf(n).forEach(pred -> {
				if (!visited.contains(pred)) {
					visited.add(pred);
					q.add(pred);
				}
			});
		}
		return visited;
	}
	
	protected Set<Unit> findExitsOfLoop(Unit head, DirectedGraph<Unit> graph) {
		if (!exitsOfLoops.containsKey(head)) {
			Set<Unit> loopNodes = loops.get(head);
			Set<Unit> exits = new HashSet<>();
			loopNodes.forEach(n -> exits.addAll(graph.getSuccsOf(n)));
			exits.removeAll(loopNodes);
			// Deal with special nested loop
			Set<Unit> outerHeads = exits
					.stream()
					.filter(u -> domFinder.getDominators(head).contains(u))
					.collect(Collectors.toSet());
			exits.removeAll(outerHeads);
			outerHeads.forEach(h -> {
				assert loops.containsKey(h); // every such node should be a head of a loop
				exits.addAll(findExitsOfLoop(h, graph));
			});
			exitsOfLoops.put(head, exits);
		}
		return exitsOfLoops.get(head);
	}
	
	public static boolean isCatchStmt(Unit u) {
		if (u instanceof IdentityStmt) {
			IdentityStmt stmt = (IdentityStmt) u;
			return stmt.getRightOp() instanceof CaughtExceptionRef;
		}
		return false;
	}
}
