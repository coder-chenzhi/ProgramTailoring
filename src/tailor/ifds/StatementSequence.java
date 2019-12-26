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

import java.util.LinkedList;

import soot.Unit;

public class StatementSequence {
	
	private final LinkedList<Unit> stmtSeq;
	private final int hashCode;
	
	private static final StatementSequence epsilon
		= new StatementSequence();
	
	public StatementSequence(LinkedList<Unit> stmtSeq) {
		// the stmtSeq should not be null!
		this.stmtSeq = stmtSeq;
		this.hashCode = stmtSeq.hashCode();
	}
	
	public StatementSequence() {
		this(new LinkedList<Unit>());
	}

	public LinkedList<Unit> getStmtSeq() {
		return stmtSeq;
	}
	
	public Unit getHead() {
		return stmtSeq.getFirst();
	}
	
	public Unit getTail() {
		return stmtSeq.getLast();
	}
	
	/** Return a *new* fact with added APICall */
	public StatementSequence addHead(Unit stmt) {
		LinkedList<Unit> newStmtSeq = new LinkedList<>(stmtSeq);
		newStmtSeq.addFirst(stmt);
		return createInstance(newStmtSeq);
	}
	
	/** Return a *new* fact whose first APICall item
	  * has been removed */
	public StatementSequence removeHead() {
		if (stmtSeq.size() > 1) {
			LinkedList<Unit> newStmtSeq = new LinkedList<>(stmtSeq);
			newStmtSeq.removeFirst();
			return createInstance(newStmtSeq);
		} else {
			return epsilon();
		}
	}
	
	public boolean contains(Unit stmt) {
		return stmtSeq.contains(stmt);
	}
	
	public int length() {
		return stmtSeq.size();
	}
	
	public boolean isEpsilon() {
		return this == epsilon();
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (this != epsilon() && // ensure that epsilon and zero value are not equivalent
				obj instanceof StatementSequence) {
			StatementSequence anoSeq = (StatementSequence) obj;
			return hashCode == anoSeq.hashCode // quickly fail
					&& stmtSeq.equals(anoSeq.stmtSeq);
		}
		return false;
	}
	
	@Override
	public String toString() {
		if (isEpsilon()) {
			return getClass().getSimpleName() + "@: Epsilon";
		} else {
			return getClass().getSimpleName() + "@: "
					+ length() + " "
					+ stmtSeq.toString();
		}
	}
	
	static StatementSequence epsilon() {
		return epsilon;
	}
	
	static StatementSequence createInstance(LinkedList<Unit> newStmtSeq) {
		return new StatementSequence(newStmtSeq);
	}
}
