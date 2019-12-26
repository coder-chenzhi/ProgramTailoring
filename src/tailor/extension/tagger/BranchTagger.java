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

package tailor.extension.tagger;

import java.util.LinkedList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BriefBlockGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.DominatorsFinder;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.MHGPostDominatorsFinder;
import tailor.icfg.util.NoExceptionGraph;
import tailor.tag.BranchTag;


public enum BranchTagger implements MethodTagger {
	// Enable singleton pattern
	INSTANCE;

	/**
	 * Given a method, tag the unit which is in an if-else or switch-case branch.
	 */
	public void tag(SootMethod m) {
		if (!m.hasTag(BranchTag.NAME)) {
			NoExceptionGraph<Block> g = new NoExceptionGraph<>(
					new BriefBlockGraph(m.getActiveBody()));
			Collection<Block> branchBlocks = findBranchNode(g);
			branchBlocks.forEach(block -> {
				block.forEach(u -> u.addTag(BranchTag.INSTANCE));
			});
			// Mark the method `m` with a BranchTag to represent that `m` 
			// has been tagged by this tagger.
			m.addTag(BranchTag.INSTANCE);
		}
	}
	
	/**
	 * Given a control-flow graph, find the node (Unit or Block) which is in
	 * an if-else or switch-case branch. Notice that only nodes within
	 * unexceptionally control flow are considered. 
	 */
	public <N> Collection<N> findBranchNode(DirectedGraph<N> graph) {
		DominatorsFinder<N> domFinder = new MHGDominatorsFinder<>(graph);
		DominatorsFinder<N> postDomFinder = new MHGPostDominatorsFinder<>(graph);
		Map<N, Set<N>> doms = new HashMap<>();
		Map<N, Set<N>> postDoms = new HashMap<>();
		graph.forEach(n -> {
			domFinder.getDominators(n).forEach(dom -> {
				if (!doms.containsKey(dom)) {
					doms.put(dom, new HashSet<>());
				}
				if (dom != n) {
					doms.get(dom).add(n);
				}
			});
			postDomFinder.getDominators(n).forEach(postDom -> {
				if (!postDoms.containsKey(postDom)) {
					postDoms.put(postDom, new HashSet<>());
				}
				if (postDom != n) {
					postDoms.get(postDom).add(n);
				}
			});
		});
		Set<N> res = new HashSet<>();
		BranchNodePred<N> branchNodePred = new BranchNodePred<>(graph, doms, postDoms);
		graph.forEach(n -> {
			if (branchNodePred.test(n)) {
				res.add(n);
			}
		});
		return res;
	}
	
	private class BranchNodePred<N> implements Predicate<N> {

		private DirectedGraph<N> graph;
		private Map<N, Set<N>> doms, postDoms;
		
		public BranchNodePred(DirectedGraph<N> graph,
				Map<N, Set<N>> doms, Map<N, Set<N>> postDoms) {
			this.graph = graph;
			this.doms = doms;
			this.postDoms = postDoms;
		}
		
		@Override
		public boolean test(N n) {
			// Handle nested and chained branches. Chained branch is
			// a special nested branch.
			int size = graph.size();
			if (doms.get(n).size() + postDoms.get(n).size() + 1 < size) {
				N branch = findLowestBranch(n);
				while (branch != null) {
					for (N branchSucc : graph.getSuccsOf(branch)) {
						if (branchSucc != n
								&& !doms.get(branchSucc).contains(n)
								&& !graph.getSuccsOf(n).contains(branchSucc)
//								&& otherBranchHasInvoke(branchSucc, branch)
								) {
							return true;
						}
					}
					branch = findLowestBranch(branch);
				}
			}
			return false;
		}
		
		@SuppressWarnings("unused")
		private boolean otherBranchHasInvoke(N n, N branch) {
			Queue<N> queue = new LinkedList<>();
			Set<N> visited = new HashSet<>();
			queue.add(n);
			while (!queue.isEmpty()) {
				N node = queue.poll();
				if (visited.add(node)) {
					if (containsInvoke(node)) {
						return true;
					}
					graph.getSuccsOf(node).forEach(succ -> {
						if (!postDoms.get(succ).contains(branch)) {
							queue.add(succ);
						}
					});
				}
			}
			return false;
		}
		
		private N findLowestBranch(N n) {
			N res = null;
			Queue<N> queue = new LinkedList<>(graph.getPredsOf(n));
			Set<N> visited = new HashSet<>();
			while (!queue.isEmpty()) {
				N node = queue.poll();
				if (node != n && visited.add(node)) {
					if (doms.get(node).contains(n)
							&& !postDoms.get(n).contains(node)) {
						res = node;
						break;
					}
					queue.addAll(graph.getPredsOf(node));
				}
			}
			return res;
		}

		private boolean containsInvoke(N n) {
			if (n instanceof Block) {
				Block b = (Block) n;
				for (Unit unit : b) {
					if (((Stmt) unit).containsInvokeExpr()) {
						return true;
					}
				}
				return false;
			} else {
				return ((Stmt) n).containsInvokeExpr();
			}
		}
	}
}
