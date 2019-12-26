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

import soot.Scene;
import soot.SootMethod;
import soot.toolkits.graph.StronglyConnectedComponentsFast;
import tailor.icfg.AbstractJimpleBasedICFG;
import tailor.icfg.util.ICFGCallGraph;
import tailor.tag.RecursionTag;

public enum RecursionTagger implements ICFGTagger {
	// Enable singleton pattern
	INSTANCE;
	
	@Override
	public void tag(AbstractJimpleBasedICFG icfg) {
		ICFGCallGraph cg = new ICFGCallGraph(icfg, Scene.v().getMainMethod());
		StronglyConnectedComponentsFast<SootMethod> cgscc =
				new StronglyConnectedComponentsFast<>(cg);
		cgscc.getTrueComponents().forEach(scc -> {
			scc.forEach(m -> {
				if (!m.hasTag(RecursionTag.NAME)) {
					m.addTag(RecursionTag.getInstance(scc));
				}
			});
		});
	}
}
