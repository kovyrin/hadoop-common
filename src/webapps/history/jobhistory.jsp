<%@ page
  contentType="text/html; charset=UTF-8"
  import="java.io.*"
  import="java.util.*"
  import="org.apache.hadoop.mapred.*"
  import="org.apache.hadoop.util.*"
  import="org.apache.hadoop.fs.*"
  import="javax.servlet.jsp.*"
  import="java.text.SimpleDateFormat"
  import="org.apache.hadoop.mapred.*"
  import="org.apache.hadoop.mapred.JobHistory.*"
%>
<%!	
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("d/MM HH:mm:ss") ;
%>
<html>
<head>
<title>Hadoop Map/Reduce Administration</title>
<link rel="stylesheet" type="text/css" href="/static/hadoop.css">
</head>
<body>
<h1>Hadoop Map/Reduce History Viewer</h1>
<hr>
<h2>Available History </h2>
<%
    PathFilter jobLogFileFilter = new PathFilter() {
      public boolean accept(Path path) {
        return !(path.getName().endsWith(".xml"));
      }
    };
    
	FileSystem fs = (FileSystem) application.getAttribute("fileSys");
	String historyLogDir = (String) application.getAttribute("historyLogDir");
	Path[] jobFiles = fs.listPaths(new Path(historyLogDir), jobLogFileFilter);

    // sort the files on creation time.
    Arrays.sort(jobFiles, new Comparator<Path>() {
      public int compare(Path p1, Path p2) {
        String[] split1 = p1.getName().split("_");
        String[] split2 = p2.getName().split("_");
        
        // compare job tracker start time
        int res = new Date(Long.parseLong(split1[1])).compareTo(
                             new Date(Long.parseLong(split2[1])));
        if (res == 0) {
          res = new Date(Long.parseLong(split1[3])).compareTo(
                           new Date(Long.parseLong(split2[3])));
        }
        if (res == 0) {
          Long l1 = Long.parseLong(split1[4]);
          res = l1.compareTo(Long.parseLong(split2[4]));
        }
        
        return res;
      }
    });

    if (null == jobFiles ){
      out.println("NULL !!!"); 
      return ; 
    }
       
    out.print("<table align=center border=2 cellpadding=\"5\" cellspacing=\"2\">");
    out.print("<tr><td align=\"center\" colspan=\"9\"><b>Available Jobs </b></td></tr>\n");
    out.print("<tr>");
    out.print("<td>Job tracker Host Name</td>" +
              "<td>Job tracker Start time</td>" +
              "<td>Job Id</td><td>Name</td><td>User</td>") ; 
    out.print("</tr>"); 
    for (Path jobFile: jobFiles) {
      String[] jobDetails = jobFile.getName().split("_");
      String trackerHostName = jobDetails[0];
      String trackerStartTime = jobDetails[1];
      String jobId = jobDetails[2] + "_" +jobDetails[3] + "_" + jobDetails[4] ;
      String user = jobDetails[5];
      String jobName = jobDetails[6];
      
%>
<center>
<%	

	  printJob(trackerHostName, trackerStartTime, jobId,
               jobName, user, jobFile.toString(), out) ; 
%>
</center> 
<%
	} // end while trackers 
%>
<%!
	private void printJob(String trackerHostName, String trackerid,
                          String jobId, String jobName,
                          String user, String logFile, JspWriter out)
    throws IOException{
	    out.print("<tr>"); 
	    out.print("<td>" + trackerHostName + "</td>"); 
	    out.print("<td>" + new Date(Long.parseLong(trackerid)) + "</td>"); 
	    out.print("<td>" + "<a href=\"jobdetailshistory.jsp?jobid="+ jobId + 
	        "&logFile=" + logFile +"\">" + jobId + "</a></td>"); 
	    out.print("<td>" + jobName + "</td>"); 
	    out.print("<td>" + user + "</td>"); 
	    out.print("</tr>");
	}
 %> 
</body></html>
