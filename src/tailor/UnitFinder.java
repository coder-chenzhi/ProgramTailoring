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

package tailor;

import java.util.LinkedHashSet;
import java.util.Set;

import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.tagkit.Host;
import soot.tagkit.LineNumberTag;
import soot.tagkit.SourceLnPosTag;
import soot.tagkit.Tag;
import tailor.icfg.ICFG;

public class UnitFinder {

	private static final String DELIM = "/";
	
	private final ICFG<Unit, SootMethod> icfg; 
	
	public UnitFinder(ICFG<Unit, SootMethod> icfg) {
		this.icfg = icfg;
	}

	/*
	 * Format:
	 * caller/callee/lineNumber
	 * 
	 * Currently, only class name and method name of caller and callee
	 * would be considered.
	 */
	public Unit find(String s) {
		String[] portions = s.split(DELIM);
		String caller = portions[0];
		String api = portions[1];
		int lineNumber = portions[2].length() == 0 ? -1 : Integer.parseInt(portions[2]);
		Set<Unit> res = inferCallSite(api, caller, lineNumber);
		if (res.isEmpty()) {
			System.err.println("[UnitFinder] Warning: missing unit \"" + s + "\" in program");
		} else {
			if (icfg != null) {
				res.retainAll(icfg.allNodes());
			}
			// Temporal fix for multiple caller candidates:
			if (res.size() >= 1) {
				return res.iterator().next();
			} else {
				System.err.println("[UnitFinder] Warning: missing unit \"" + s + "\" in ICFG");
			}
		}
		return null;
	}
	
	public static boolean isSpecificCall(Unit api, String className, String methName) {
		if (api instanceof Stmt) {
			Stmt stmt = (Stmt) api;
			if (stmt.containsInvokeExpr()) {
				SootMethod callee = stmt.getInvokeExpr().getMethod();
				return callee.getDeclaringClass().getName().equals(className) &&
						callee.getName().equals(methName);
			}
		}
		return false;
	}
	
	private Set<Unit> inferCallSite(String api, String caller, int lineNumber) {
		String callerClassName = getClassName(caller);
		String callerMethodName = getMethodName(caller);
		if(!Scene.v().containsClass(callerClassName)) {
			// Try not to load the absent class since this may cause the modification of
			// class hierarchy so that the active points-to analysis (spark.pag.PAG by default)
			// will be set to null. If we try to get points-to analysis after that, we will
			// obtain an useless DumbPointerAnalysis instead of PAG. 
			throw new RuntimeException("Relation file refers to unknown class: "+ callerClassName);
		}

		SootClass sootClass = Scene.v().getSootClass(callerClassName);
		Set<SootMethod> methodsWithRightName = new LinkedHashSet<SootMethod>();
		for (SootMethod m: sootClass.getMethods()) {
			if(m.isConcrete() && m.getName().equals(callerMethodName)) {
				methodsWithRightName.add(m);
			}
		}
		
		String apiClassName = getClassName(api);
		String apiMethodName = getMethodName(api);
		Set<Unit> res = new LinkedHashSet<Unit>();
		for (SootMethod method : methodsWithRightName) {
			if (method.isConcrete()) {
				if (!method.hasActiveBody()) {
					method.retrieveActiveBody();
				}
				Body body = method.getActiveBody();
				for (Unit u : body.getUnits()) {
					if (coversLineNumber(lineNumber, u)) {
						if (isSpecificCall(u, apiClassName, apiMethodName)) {
							res.add(u);
							Debug.println("[UnitFinder] Found: "
									+ "[" + apiMethodName + "]"
									+ " in " + method.getSignature());
						}
					}
				}
			}
		}
		return res;
	}
	
	private String getClassName(String s) {
		if (s.startsWith("<")) {
			return s.split(":")[0].substring(1);
		} else {
			return s.substring(0, s.lastIndexOf('.'));
		}
	}
	
	private String getMethodName(String s) {
		if (s.startsWith("<")) {
			String sig = s.split(" ")[2];
			return sig.substring(0, sig.indexOf('('));
		} else {
			return s.substring(s.lastIndexOf('.') + 1);
		}
	}
	
	private boolean coversLineNumber(int lineNumber, Host host) {
		{
			SourceLnPosTag tag = (SourceLnPosTag) host.getTag("SourceLnPosTag");
			if(tag!=null) {
				if(tag.startLn()<=lineNumber && tag.endLn()>=lineNumber) {
					return true;
				}
			}
		}
		{
			int ln = host.getJavaSourceStartLineNumber();
			if (lineNumber == ln && ln != -1) {
				return true;
			}
		}
		return false;
	}
	
	public static int getLineNumber(Unit unit) {
		int res = Integer.MAX_VALUE;
		for (Tag tag : unit.getTags()) {
			if (tag instanceof LineNumberTag) {
				LineNumberTag ln_tag = (LineNumberTag)tag;
				int lineno = ln_tag.getLineNumber();
				res = Math.min(res, lineno);
			}
		}
		if (res == Integer.MAX_VALUE) {
			res = -1;
		}
		return res;
	}
}
