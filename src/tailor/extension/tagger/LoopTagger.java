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

import heros.solver.Pair;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import tailor.icfg.util.IntraCycleFinder;
import tailor.icfg.util.NoExceptionGraph;
import tailor.tag.LoopTag;

public enum LoopTagger implements MethodTagger {
	// Enable singleton pattern
	INSTANCE;
	
	private IntraCycleFinder cycleFinder = new IntraCycleFinder();
	
	public void tag(SootMethod m) {
		if (!m.hasTag(LoopTag.NAME)) {
			DirectedGraph<Unit> graph =
					new NoExceptionGraph<>(new BriefUnitGraph(m.getActiveBody()));
			Set<Unit> loopNodes = computeLoopNodes(graph);
			loopNodes.forEach(u -> u.addTag(LoopTag.INSTANCE));
			// Mark the method `m` with a LoopTag to represent that `m` 
			// has been tagged by this tagger.
			m.addTag(LoopTag.INSTANCE);
		}
	}
	
	public Set<Unit> computeLoopNodes(DirectedGraph<Unit> graph) {
		Set<Pair<Unit, Unit>> backEdges = cycleFinder.findBackEdges(graph);
		Map<Unit, Set<Unit>> loops = cycleFinder.computeLoops(graph, backEdges);
		Set<Unit> res = new HashSet<>();
		loops.values().forEach(res::addAll);
		return res;
	}
}
