#!/usr/bin/env python
import os, sys

SRC_DIR = 'src'
LIB_DIR = 'lib'
BUILD_DIR = os.path.join('build', 'bin')
if 'win' in sys.platform:
 PATH_SEP = ';'
else:
 PATH_SEP = ':'

def listFiles(d):
 result = []
 for root, dirs, files in os.walk(d):
  for file in files:
   result.append(os.path.join(root, file))
 return result

def getAllJavaFiles(d):
 return [file for file in listFiles(d) if os.path.splitext(file)[1] == '.java']

if __name__ == '__main__':
 if not os.path.exists(BUILD_DIR):
  os.makedirs(BUILD_DIR)
 cp = PATH_SEP.join(listFiles(LIB_DIR) + [SRC_DIR])
 java_files = getAllJavaFiles(SRC_DIR)
 os.system('javac -cp %s -d %s %s' % (cp, BUILD_DIR, ' '.join(java_files)))
 os.system('jar -cvf %s -C %s .' % (os.path.join('build', 'tailor.jar'), BUILD_DIR))
