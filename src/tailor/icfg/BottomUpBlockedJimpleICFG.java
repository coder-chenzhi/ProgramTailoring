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

import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;

/**
 * For clarity w.r.t. TopDownBlockedJimpleICFG
 *
 */
public class BottomUpBlockedJimpleICFG extends BlockedJimpleBasedICFG {

	public BottomUpBlockedJimpleICFG(Set<Unit> specUnits) {
		super(specUnits);
	}
	
	public BottomUpBlockedJimpleICFG(Set<Unit> specUnits,
			Map<Pair<Unit, Unit>, Set<Unit>> intraCycles,
			Map<Unit, Set<SootMethod>> interCycles) {
		super(specUnits, intraCycles, interCycles);
	}
	
	public BottomUpBlockedJimpleICFG() {}
}
