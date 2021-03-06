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

import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;

/**
 * A host (typically Unit) has this tag if it is reachable from main method.
 *
 */
public enum MainReachableTag implements Tag {
	// Enable singleton pattern
	INSTANCE;
	
	public static final String NAME = "MainReachableTag";
	
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public byte[] getValue() throws AttributeValueException {
		throw new UnsupportedOperationException("ReachableTag: getValue");
	}
}
