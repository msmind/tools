import java.util.regex.Pattern

moduleDir = System.getProperty("user.dir")

def result 
def getClasspathEntries //predefine closIre name
getClasspathEntries={ moduleName-> 
    try {
    new File(moduleDir + moduleName + "/.classpath").eachLine{ line ->
     if (line ==~ /.*kind="src".*/ ){
         m = (line =~ /.* path="\/(.*)"/)
         if (m.size() != 0 && !result.contains(m[0][1])) {
             result << m[0][1];
             getClasspathEntries(m[0][1]);
         }
     }
    }
    } catch (e){
    }
}
cnt = 0
new File (moduleDir).eachDirMatch({it ==~ /.*Module(|EAR)$/}){ dirName ->
    matcher = (dirName =~ /[\\](?i)([0-9a-z]*)$/)
    moduleName = matcher[0][1]
    result = []
    getClasspathEntries(moduleName)
    println moduleName+ ":" + result.join('?')
    cnt++
}
println cnt;
//getClasspathEntries(moduleDir + moduleName)

