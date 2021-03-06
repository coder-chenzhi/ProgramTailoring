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

package tailor.icfg;

import heros.solver.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import soot.SootMethod;
import soot.Unit;
import tailor.tag.BottomUpReachableTag;

/**
 * For IFDSTopDownTailor analysis. i.e., 
 * In the top down process, avoid traversing the nodes 
 * in ICFG which have not been traversed by IFDSBottomUpTailor analysis.
 * 
 */
public class TopDownBlockedJimpleICFG extends BlockedJimpleBasedICFG {

	public TopDownBlockedJimpleICFG(Set<Unit> specUnits) {
		super(specUnits);
	}
	
	public TopDownBlockedJimpleICFG(Set<Unit> specUnits,
			Map<Pair<Unit, Unit>, Set<Unit>> intraCycles,
			Map<Unit, Set<SootMethod>> interCycles) {
		super(specUnits, intraCycles, interCycles);
	}
	
	public TopDownBlockedJimpleICFG() {
		super();
	}
	
	@Override
	public List<Unit> getSuccsOf(Unit u) {
		return super.getSuccsOf(u)
				.stream()
				.filter(unit -> unit.hasTag(BottomUpReachableTag.NAME))
				.collect(Collectors.toList());
	}
}
