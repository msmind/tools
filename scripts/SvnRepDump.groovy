import static Constants.*

class Constants {
    static final LS = System.getProperty('line.separator')
    static final FS = File.separator
}


public String tail( File file, int lines) {
    try {
        java.io.RandomAccessFile fileHandler = new java.io.RandomAccessFile( file, "r" );
        long fileLength = file.length() - 1;
        StringBuilder sbLine = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        int line = 0;

        for( long filePointer = fileLength; filePointer != -1; filePointer-- ) {
            fileHandler.seek( filePointer );
            int readByte = fileHandler.readByte();

            if( readByte == 0xA   ) {
                line = line + 1;
                sbLine.append( LS.reverse() );
                sbLine.reverse()
                
                sb.append(sbLine)
                sbLine = new StringBuilder()
                if (line == lines) {
                    if (filePointer == fileLength) {
                        continue
                    } else {
                        break
                    }
                }

                continue
            } else if( readByte == 0xD ) {
                continue
            }
           sbLine.append( ( char ) readByte )
        }

        return sb.toString()
    } catch( java.io.FileNotFoundException e ) {
        e.printStackTrace()
        return null
    } catch( java.io.IOException e ) {
        e.printStackTrace()
        return null
    }

}
def today = new Date().format('yyyy-MM-dd')
def workDir = System.getProperty("user.dir")
def successLogLinePattern = ~/\* Dumped revision (\d+)\.$/
def pathPattern = ~"^(.*\\${FS})([^\\${FS}]+)(\\${FS}|)\$"

for (a in this.args) {
    matcher = pathPattern.matcher(a)
    if (!matcher.matches()) {
        workDir = System.getProperty("user.dir") + FS
        repName = a
    } else {
        workDir = matcher[0][1]
        repName = matcher[0][2]
    }
    if ( !new File("${workDir}${repName}${FS}format").exists() ){
        println "Bad repository ${workDir}${repName}${FS}"
        break
    }
    int lower = 0
    upper = 'HEAD'
    isFirstDump = true
    int latestDumpedRev = -1
    int shiftToRev = 0
    def proc
  
    def fErr = new File(workDir + 'svndump.log')
    def bwtrErr= new BufferedOutputStream(new FileOutputStream(fErr, true), 512)
    line = "-------------------------------${LS}"
    bwtrErr.write(line.getBytes(), 0, line.length())
    line = "Repository $a.${LS}"
    bwtrErr.write(line.getBytes(), 0, line.length())
    line = "-------------------------------${LS}"
    bwtrErr.write(line.getBytes(), 0, line.length())

    def bwtrOut
    try {
        while(true){
            def revRange = "$lower-$upper"
            fileName = "${repName}_${today}_($revRange).dump"

            bwtrOut= new BufferedOutputStream(new FileOutputStream(workDir + "${fileName}"), 512)
            def command
            if (isFirstDump){
                command = "svnadmin dump -r$lower:$upper $a"
                isFirstDump = false
            } else {
                command = "svnadmin dump -r$lower:$upper --incremental $a"
            }
            println command
            proc = command.execute( null, new File( workDir ) )
            proc.waitForProcessOutput(bwtrOut, bwtrErr)
            bwtrErr.flush()
            bwtrOut.close()

            errCode = proc.exitValue()
            logFile = tail(fErr, 5)

            boolean isError = false
            logFile.split(LS).find{line -> 
                matcher = successLogLinePattern.matcher(line)
                res = matcher.matches()
                if (res) {
                    upper = matcher[0][1]
                } else if ( !isError && line.trim() != '') {
                    isError = true
                }
                res 
            }
            println "All revisions dumped: ${!isError}." 
            try {
                intUpper = Integer.parseInt(upper)
                if (isError) {
                    intUpper++
                }
            } catch (e) {
                intUpper = lower
            }
            println "Latest succesfully dumped revision is $intUpper."
            revRange = "$lower-$intUpper"
            if (lower >= intUpper) {
                revRange = "$lower"
            }

            command = "mv ${fileName} ${repName}_${today}_($revRange).dump"
            println command
            proc = command.execute( null, new File( workDir ) )
            proc.waitFor()
            println "Revisions $revRange have been dumped to file ${repName}_${today}_($revRange).dump\n"

            if (!isError){
                break
            }
            if (latestDumpedRev == intUpper){
                shiftToRev++
            } else {
                latestDumpedRev = intUpper
                shiftToRev = 1
            }
            lower = intUpper + shiftToRev 
            upper = "HEAD"    
        }
    } catch (e) {
        println "Error occured: " + e.getMessage()
    } finally {
        try {
            bwtrOut.close()
            bwtrErr.close()
        } finally {
        }
    }
}
