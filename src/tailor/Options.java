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

import java.util.ArrayList;
import java.util.List;

public class Options {

	private static String scFileName;
	private static String outDir = "output";
	private static boolean retainCycle = true;
	private static boolean extendSC = true;
	private static boolean excludeLibrary = false;
	private static boolean excludeLibraryExtension = false;
	private static List<String> blockPackagePrefixs = new ArrayList<>();

	public static String[] processArgs(String[] args) {
		List<String> newArgs = new ArrayList<>();
		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-sc-file")) {
				setSCFileName(args[i + 1]);
				++i;
			} else if (args[i].equals("-out-dir")) {
				setOutDir(args[i + 1]);
				++i;
			} else if (args[i].equals("-retain-cycle")) {
				setRetainCycle(Boolean.parseBoolean(args[i + 1]));
				++i;
			} else if (args[i].equals("-extend-sc")) {
				setExtendSC(Boolean.parseBoolean(args[i + 1]));
				++i;
			} else if (args[i].equals("-exclude-library")) {
				setExcludeLibrary(Boolean.parseBoolean(args[i + 1]));
				++i;
			} else if (args[i].equals("-exclude-library-extension")) {
				setExcludeLibraryExtension(Boolean.parseBoolean(args[i + 1]));
				++i;
			} else if (args[i].equals("-block-package-prefix")) {
				addBlockPackagePrefixs(args[i + 1]);
				++i;
			} else {
				newArgs.add(args[i]);
			}
		}
		return newArgs.toArray(new String[0]);
	}

	public static String getSCFileName() {
		return scFileName;
	}

	public static void setSCFileName(String scFileName) {
		Options.scFileName = scFileName;
	}

	public static String getOutDir() {
		return outDir;
	}

	public static void setOutDir(String outDir) {
		Options.outDir = outDir;
	}

	public static boolean isRetainCycle() {
		return retainCycle;
	}

	public static void setRetainCycle(boolean retainCycle) {
		Options.retainCycle = retainCycle;
	}

	public static boolean isExtendSC() {
		return extendSC;
	}

	public static void setExtendSC(boolean extendSC) {
		Options.extendSC = extendSC;
	}

	public static boolean isExcludeLibrary() {
		return excludeLibrary;
	}

	public static void setExcludeLibrary(boolean excludeLibrary) {
		Options.excludeLibrary = excludeLibrary;
	}

	public static boolean isExcludeLibraryExtension() {
		return excludeLibraryExtension;
	}

	public static void setExcludeLibraryExtension(
			boolean excludeLibraryExtension) {
		Options.excludeLibraryExtension = excludeLibraryExtension;
	}

	public static List<String> getBlockPackagePrefixs() {
		return blockPackagePrefixs;
	}

	public static void addBlockPackagePrefixs(String prefix) {
		Options.blockPackagePrefixs.add(prefix);
	}
	
}
