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

package tailor.tag;

import java.util.List;

import soot.SootMethod;
import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;

/**
 * A SootMethod has this tag if it is in a strongly connected component
 * which causes recursion.
 *
 */
public class RecursionTag implements Tag {
	
	public final static String NAME = "RecursionTag";
	
	private static final RecursionTag INSTANCE = new RecursionTag(null);
	
	private List<SootMethod> scc;
	
	private RecursionTag(List<SootMethod> scc) {
		this.scc = scc;
	}
	
	public static RecursionTag getInstance(List<SootMethod> scc) {
		if (scc == null) {
			return INSTANCE;
		} else {
			return new RecursionTag(scc);
		}
	}
	
	public List<SootMethod> getSCC() {
		return scc;
	}
	
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public byte[] getValue() throws AttributeValueException {
		throw new UnsupportedOperationException("RecursionTag: getValue");
	}
}
