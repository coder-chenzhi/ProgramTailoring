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

import java.util.Map;
import java.util.Set;

import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.StronglyConnectedComponentsFast;
import tailor.icfg.AbstractJimpleBasedICFG;
import tailor.icfg.util.ICFGCallGraph;
import tailor.icfg.util.IntraCycleFinder;
import tailor.icfg.util.NoExceptionGraph;
import tailor.tag.InterCycleTag;
import tailor.tag.IntraCycleTag;

/**
 * A tagger which marks the units and methods that in a cycle.
 * The units in loop will be attached an IntraCycleTag.
 * The methods in cycle will be attached an InterCycleTag.
 * There are two cases for methods:
 * 1. a method can be reached from a inter-procedural cycles, i.e., recursion.
 * 2. a method can be reached from a intra-procedural cycles, i.e., loops.
 *
 */
public enum CycleTagger implements ICFGTagger {
	// Enable singleton pattern
	INSTANCE;
	
	private IntraCycleFinder cycleFinder = new IntraCycleFinder();
	
	@Override
	public void tag(AbstractJimpleBasedICFG icfg) {
		// Tag inter-procedural cycles, i.e., recursion.
		ICFGCallGraph cg = new ICFGCallGraph(icfg, Scene.v().getMainMethod());
		StronglyConnectedComponentsFast<SootMethod> cgscc =
				new StronglyConnectedComponentsFast<>(cg);
		cgscc.getTrueComponents().forEach(scc -> {
			scc.forEach(m -> tagAllReachableMethods(m, cg, InterCycleTag.INSTANCE));
		});
		
		// Tag intra-procedural cycles, i.e., loops.
		icfg.allMethods().forEach(m -> {
			DirectedGraph<Unit> graph =
					new NoExceptionGraph<>(new BriefUnitGraph(m.getActiveBody()));
			Set<Pair<Unit, Unit>> backEdges = cycleFinder.findBackEdges(graph);
			Map<Unit, Set<Unit>> loops = cycleFinder.computeLoops(graph, backEdges);
			loops.values().forEach(loopNodes -> loopNodes.forEach(u -> {
				tagUnit(u, icfg, cg, IntraCycleTag.INSTANCE);
			}));
		});
	}

	private void tagUnit(Unit u,
			AbstractJimpleBasedICFG icfg,
			ICFGCallGraph cg,
			Tag t) {
		if (!u.hasTag(t.getName())) {
			u.addTag(t);
			if (icfg.isCallStmt(u)) {
				icfg.getCalleesOfCallAt(u).forEach(callee -> {
					tagAllReachableMethods(callee, cg, t);
				});
			}
		}
	}
	
	private void tagAllReachableMethods(SootMethod m, ICFGCallGraph cg, Tag t) {
		if (!m.hasTag(t.getName())) {
			m.addTag(t);
			cg.getSuccsOf(m).forEach(callee -> tagAllReachableMethods(callee, cg, t));
		}
	}
}
