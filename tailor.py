#!/usr/bin/env python
import os, sys

if 'win' in sys.platform:
 PATH_SEP = ';'
else:
 PATH_SEP = ':'

def processArg(args):
 argMap = {}
 i = 0
 while i < len(args):
  if args[i] == '-sc-file':
   argMap['-sc-file'] = args[i + 1]
   i += 1
  elif args[i] == '-out-dir':
   argMap['-out-dir'] = args[i + 1]
   i += 1
  elif args[i] == '-extend-sc':
   argMap['-extend-sc'] = args[i + 1]
   i += 1
  elif args[i] == '-jre-lib':
   argMap['-jre-lib'] = args[i + 1]
   i += 1
  elif args[i] == '-cp':
   if not argMap.has_key('-cp'):
    argMap['-cp'] = []
   for cp in args[i + 1].split(PATH_SEP):
    argMap['-cp'].append(cp)
   i += 1
  elif args[i] == '-main-class':
   argMap['-main-class'] = args[i + 1]
   i += 1
  elif args[i] == '-reflection-log':
   argMap['-reflection-log'] = args[i + 1]
   i += 1
  elif args[i] == '-help':
   argMap['-help'] = True
  else:
   raise RuntimeError('Unknown option: %s' % args[i])
  i += 1
 return argMap

def generateCommand(argMap):
 cmd = 'java -cp %s%s%s ' \
   % (os.path.join('build', 'tailor.jar'), \
      PATH_SEP, \
	  os.path.join('lib', '*'))
 cmd += '-Xmx64G tailor.Driver '
 cmd += '-sc-file %s ' % argMap['-sc-file']
 if argMap.has_key('-out-dir'):
  cmd += '-out-dir %s ' % argMap['-out-dir']
 if argMap.has_key('-extend-sc'):
  cmd += '-extend-sc %s ' % argMap['-extend-sc']
 cmd += '-w -app '
 cmd += '-cp '
 if argMap.has_key('-jre-lib'):
  jredir = argMap['-jre-lib']
  jre = ['rt.jar', 'jce.jar', 'jsse.jar']
  if not os.path.exists(os.path.join(jredir, jre[0])):
   raise RuntimeError('Invalid JRE directory: %s' % jredir)
  cmd += PATH_SEP.join([os.path.join(jredir, jar) for jar in jre])
 if argMap.has_key('-cp'):
  cmd += PATH_SEP + PATH_SEP.join(argMap['-cp']) + ' '
 cmd += '-p cg.spark enabled '
 if argMap.has_key('-reflection-log'):
  cmd += '-p cg reflection-log:%s ' % argMap['-reflection-log']
 cmd += '-keep-line-number -src-prec c -f n '
 if argMap.has_key('-main-class'):
  mainclass = argMap['-main-class']
  cmd += '-main-class %s %s' % (mainclass, mainclass)
 return cmd

def usage():
 print ' -help                        Print this message'
 print
 print ' -sc-file <file>              The file containing the sequential criteria'
 print
 print ' -extend-sc <true or false>   Enable or disable SC extension (default value: true)'
 print
 print ' -out-dir <dir>               The directory containing analysis results'
 print '                              (default value: output)'
 print
 print ' -cp <jar or dir>             The application jar file or the directory containing'
 print '                              the classes of the application. Use path separator'
 print '                              (":" on Linux/Unix/Mac OS or ";" on Windows) while '
 print '                              specifying multiple items'
 print
 print ' -jre-lib <dir>               The directory containing the JRE to be used'
 print '                              for whole-program anlaysis'
 print
 print ' -main-class <class>          Name of the main class of the application'
 print
 print ' -reflection-log <file>       The reflection log file for the application for'
 print '                              resolving reflective call sites'
 print
 

if __name__ == '__main__':
 argMap = processArg(sys.argv[1:])
 if argMap.has_key('-help'):
  usage()
 else:
  cmd = generateCommand(argMap)
  print cmd
  os.system(cmd)
