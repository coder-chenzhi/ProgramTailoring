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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

import soot.Unit;
import soot.jimple.NopStmt;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.DirectedGraph;

/**
 * Encapsulate a directed graph, remove the node about exception. Exception
 * would affect the identifiability of branches and loops. The use of this
 * graph may cause the missing of statements within catch clauses.
 *
 * @param <N>
 */
public class NoExceptionGraph<N> implements DirectedGraph<N> {

	private DirectedGraph<N> delegate;
	private Collection<N> reachable;
	
	private List<N> heads, tails;
	private Map<N, List<N>> preds = new HashMap<>();
	
	/*
	 * Construct a directed graph without exception.
	 */
	public NoExceptionGraph(DirectedGraph<N> delegate) {
		this.delegate = delegate;
		computeReachableNode(delegate);
	}
	
	private void computeReachableNode(DirectedGraph<N> graph) {
		Stack<N> stack = new Stack<>();
		delegate.getHeads()
			.stream()
			.filter(head -> !IntraCycleFinder.isCatchStmt(firstUnitOf(head)))
			.distinct()
			.forEach(head -> {
				// Exclude some strange illegal cases.
				if (!(firstUnitOf(head) instanceof NopStmt)) {
					stack.push(head);
				}
		});
		// the graph only has **one** head if exception
		// is not taken into account.
//		assert stack.size() == 1;
		if (stack.size() != 1) {
			System.out.println("[Heads]: ");
			stack.forEach(System.out::println);
			System.out.println("************");
		}
		reachable = new HashSet<>();
		while (!stack.isEmpty()) {
			N n = stack.pop();
			reachable.add(n);
			graph.getSuccsOf(n).forEach(succ -> {
				if (!reachable.contains(succ)) {
					stack.push(succ);
				}
			});
		}
	}
	
	@Override
	public List<N> getHeads() {
		if (heads == null) {
			heads = delegate.getHeads()
					.stream()
					.filter(reachable::contains)
					.collect(Collectors.toList());
		}
		return heads;
	}

	@Override
	public List<N> getTails() {
		if (tails == null) {
			tails = delegate.getTails()
					.stream()
					.filter(reachable::contains)
					.collect(Collectors.toList());
		}
		return tails;
	}

	@Override
	public List<N> getPredsOf(N s) {
		if (!preds.containsKey(s)) {
			preds.put(s, delegate.getPredsOf(s)
				.stream()
				.filter(reachable::contains)
				.collect(Collectors.toList()));
		}
		return preds.get(s);
	}

	@Override
	public List<N> getSuccsOf(N s) {
		return delegate.getSuccsOf(s);
	}

	@Override
	public int size() {
		return reachable.size();
	}

	@Override
	public Iterator<N> iterator() {
		return reachable.iterator();
	}
	
	private Unit firstUnitOf(N n) {
		if (n instanceof Block) {
			return ((Block) n).getHead();
		} else {
			return (Unit) n;
		}
	}
}
