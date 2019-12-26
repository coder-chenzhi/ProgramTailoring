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

package tailor.ifds;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import soot.Unit;

public class StatementSequenceUtil {
	
	private Set<Unit> UnitSet;
	private Set<Unit> headSet;
	private Set<Unit> tailSet;
	private Map<Unit, Set<LinkedList<Unit>>> UnitToFollowSet;
	private Map<Unit, Set<Unit>> extension;
	
	public StatementSequenceUtil(Set<Unit> UnitSet,
			Set<Unit> headSet,
			Set<Unit> tailSet,
			Map<Unit, Set<LinkedList<Unit>>> UnitToFollowSet) {
		this.UnitSet = UnitSet;
		this.headSet = headSet;
		this.tailSet = tailSet;
		this.UnitToFollowSet = UnitToFollowSet;
		setExtension(Collections.emptyMap());
	}
	
	public void setExtension(Map<Unit, Set<Unit>> extension) {
		this.extension = extension;
	}
	
	public boolean canAddHead(Unit Unit, StatementSequence fact) {
		Set<LinkedList<Unit>> followSet = UnitToFollowSet.get(Unit);
		if (followSet != null) {
			return followSet.contains(fact.getStmtSeq()); 
		}
		return false;
	}
	
	public boolean canExtend(Unit apiCall, StatementSequence fact) {
		if (!fact.getStmtSeq().isEmpty()) {
			if (fact.getStmtSeq().contains(apiCall)) {
				return false;
			}
			
			Unit head = fact.getHead();
			if (isUnitOfSC(head) && !isSCHead(head)) {
				return false;
			}
			
			Set<Unit> ext = extension.get(head);
			if (ext != null) {
				return ext.contains(apiCall);
			}
		}
		return false;
	}
	
	public boolean canRemoveHead(Unit Unit, StatementSequence fact) {
		if (fact.getStmtSeq().isEmpty()) {
			return false;
		} else {
			return fact.getStmtSeq().getFirst().equals(Unit);
		}
	}
	
	public boolean isUnitOfSC(Unit Unit) {
		return UnitSet.contains(Unit);
	}
	
	public boolean isSCHead(Unit Unit) {
		return headSet.contains(Unit);
	}
	
	public boolean isTail(Unit Unit) {
		return tailSet.contains(Unit);
	}
	
	public StatementSequence createZeroValue() {
		return new StatementSequence();
	}
}
