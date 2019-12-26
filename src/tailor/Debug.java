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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.JimpleIFDSSolver;
import tailor.icfg.ICFG;

public class Debug {
	private static boolean debug = false;
	private static boolean display = false;
	
	private static String fileName = Paths
			.get(Options.getOutDir(), "tailor-log.txt")
			.toString();
	
	private static BufferedWriter out;
	
	static {
		try {
			File file = new File(fileName);
			file.getParentFile().mkdirs();
			if (file.exists()) {
				file.delete();
			}
			file.createNewFile();
			out = new BufferedWriter(new FileWriter(file));
		} catch (Exception e) {}
	}
	
	public static boolean getDebug() { return debug; }
	
	public static void setDebug(boolean debug) { Debug.debug = debug; }
	
	public static boolean getDisplay() { return display; }
	
	public static void setDisplay(boolean display) { Debug.display = display; }
	
	public static void close() {
		try {
			out.flush();
			out.close();
		} catch (Exception e) {}
	}
	
	public static void print(Object o) { 
		if (debug) {
			try {
				out.write(o.toString());
			} catch (Exception e) {}
			if (display) {
				System.out.print(o);
			}
		}
	}

	public static void println(Object o) { 
		if (debug) {
			try {
				out.write(o + "\n");
			} catch (Exception e) {}
			if (display) {
				System.out.println(o);
			}
		}
	}

	public static String unitToString(Unit u, ICFG<Unit, SootMethod> icfg) {
		return "{" + icfg.getMethodOf(u) + "}: <" + u + "> "
				+ "(Ln: " + u.getJavaSourceStartLineNumber() + ")"; 
	}

	public static void queryResultsOf(JimpleIFDSSolver<?, ?> solver, SootMethod m) {
		Debug.println("======================================================================================");
		Debug.println("IFDS results in :" + m);
		m.getActiveBody().getUnits().
			forEach(u -> {
				Debug.println("%%: {" + u + "}");
				Set<?> results = solver.ifdsResultsAt(u);
				results.forEach(fact -> Debug.println("= " + fact));
				Debug.println("-");
			});
		Debug.println("--------------------------------------------------------------------------------------");
	}

	public static void queryResultsOf(JimpleIFDSSolver<?, ?> solver,
			ICFG<Unit, SootMethod> icfg,
			String info) {
		icfg.allMethods()
		.stream()
		.filter(m -> m.getDeclaringClass().isApplicationClass())
		.sorted((m1, m2) -> {
			if (m1.getDeclaringClass().equals(m2.getDeclaringClass())) {
				return m1.getSignature().compareTo(m2.getSignature());
			} else {
				return m1.getDeclaringClass().getName()
						.compareTo(m2.getDeclaringClass().getName());
			}
		})
		.forEach(m -> {
			Debug.println(info);
			queryResultsOf(solver, m);
		});
	}
}
