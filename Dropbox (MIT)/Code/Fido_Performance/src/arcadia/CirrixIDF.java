package arcadia;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import java.net.*;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.perf4j.log4j.Log4JStopWatch;

import org.apache.commons.lang3.StringUtils;

public class CirrixIDF {
	private static Logger log = null;
     private static String IP_Host;
	private final static String contentType = Consts.CSV;   //Consts.CSV or Consts.JSON  
	private static String getTimeBlock(Calendar cal){
		int hours = cal.get(Calendar.HOUR_OF_DAY);
		for (int i = 0; i<Consts.LISTING_TIMES.length; i++){
			if (hours >= Consts.LISTING_TIMES[i]-Consts.TIME_BUFFER && hours <= Consts.LISTING_TIMES[i]+Consts.TIME_BUFFER){
				return Consts.LISTING_STRINGS[i];
			}
		}
		DateFormat df = new SimpleDateFormat("HH.mm");
		return df.format(cal.getTime());
	}
     private static String getIP() throws UnknownHostException{
        InetAddress ipAddress = InetAddress.getByName("api.lendingclub.com"); // WARNING:  this will return the IP address in the system HOSTS file, if it exists.
        String ipAddressNum= ipAddress.getHostAddress();
        return ipAddressNum;
      }	
	public static void main(String[] args){
            //grab ordering parameters from configuration file, if it exists
            String configFile = "/home/master/fidoconfig/Cirrix IDF 1 Order Entry Controls.xlsx";
//             String configFile = "C:/Users/Jonathan Green/Documents/NetBeansProjects/DZCC2/config/Cirrix IDF 1 Order Entry Controls.xlsx";
           String account = "CIDF_";
            Config params = new Config (configFile, account);
            params.readFile();
          
		/////////////////////////////////////////////////////////////////////
		/////////				 CONTROL PARAMETERS					/////////
		/////////////////////////////////////////////////////////////////////
		double purchaseTarget = params.orderMax;
		double availableCash = purchaseTarget;
		boolean watchForNew;
		if (params.watch.equals("YES")){ //"Do you want this ordering session to wait for the new listings?"
			watchForNew = true;
		}else if (params.watch.equals("NO")){
			watchForNew = false;
		}
		boolean isWholeValid = true, isFractionalValid = false;
		boolean is36Valid, is60Valid;
		if (params.termAllowance.equals("YES")){ //"Do you want to restrict your oders to 36M loans?"
			is36Valid=true;
			is60Valid=false;
		} else {                     
			is36Valid=true;
			is60Valid=true;
		}
		
		
		/////////////////////////////////////////////////////////////////////
		/////////						SETUP						/////////
		/////////////////////////////////////////////////////////////////////
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Jdk14Logger");
		String pathToLogs = Consts.PATH_TO_LOGS+CirrixIDF.class.getName()+"/";
		Calendar cal = Calendar.getInstance();
		System.setProperty("logfile.name", pathToLogs+cal.get(Calendar.YEAR)+"/"+Consts.MONTHS[cal.get(Calendar.MONTH)]+"/"+cal.get(Calendar.DATE)+"-"+getTimeBlock(cal)+".txt");
		log = Logger.getLogger(CirrixIDF.class.getName());
         try {
            IP_Host=getIP();
          System.out.println("IP_Host: " + IP_Host);
             } catch (UnknownHostException e) {
         log.info("Error (UnknownHostException): " + e);
             }

		APIConnection api = new APIConnection(Consts.SCHEME, Consts.HOST, Consts.LISTING_PATH, Consts.CIDF_REAL_TOKEN, Consts.CIDF_REAL_AID, log);
//		APIConnection api = new APIConnection(Consts.SCHEME, Consts.HOST, Consts.LISTING_PATH, Consts.CIDF_ORDER_PATH, Consts.CIDF_REAL_TOKEN, Consts.CIDF_REAL_AID, log);
		Filter filt = new Filter(Consts.CIDF_MIN_INCOME, Consts.CIDF_DTI_INQ_FICO, Consts.CIDF_FICO_INQUIRIES_CHECK, Consts.CIDF_STATE_LOAN_AMOUNT_CHECK, Consts.CIDF_FICO_LOAN_CHECK, params.TERM_SUBGRADE_CHECK, Consts.CIDF_INVALID_GRADES, Consts.CIDF_INVALID_PURPOSES, Consts.CIDF_EMP_LENGTH_MIN, Consts.CIDF_DTI_MAX, Consts.CIDF_DTI_TERM_CHECK, Consts.CIDF_FICO_LOW_THRESHHOLD, isWholeValid, isFractionalValid, is36Valid, is60Valid, params.creditModel);

		Scorer scorer = new Scorer(Consts.CIDF_INQUIRIES_BONUSES, Consts.CIDF_FICO_BONUSES, Consts.CIDF_USAGE_BONUSES, Consts.CIDF_STATE_BONUSES, Consts.CIDF_GRADE_BONUSES, Consts.CIDF_TERM_BONUSES, Consts.CIDF_WILL_COMPETE_BONUS);

          Log4JStopWatch stopwatch = new Log4JStopWatch(log);

		/////////////////////////////////////////////////////////////////////
		/////////					  RETRIEVAL						/////////
		/////////////////////////////////////////////////////////////////////
//		log.info("Invocation: "+ args[0] + "  " + args[1] + "  " + args[2] + "  " + args[3] + "  " + args[4]);
          log.info("Purchase Limit: " + purchaseTarget);
          log.info("Wait for new listings?: " + params.watch);
          log.info("Restrict order to 36M loans?: " + params.termAllowance);
          log.info("Credit models: " + params.creditModel);
          log.info("Excluded 36M subgrades: " + params.INVALID_SUBGRADES_36);
          log.info("Excluded 60M subgrades: " + params.INVALID_SUBGRADES_60);
//          log.info("Excluded grades: " + Consts.CIDF_INVALID_GRADES);
          log.info("Invalid purposes: " + Consts.CIDF_INVALID_PURPOSES);
          log.info("Loans retrieved using contentType: " + contentType);
		LoanList ll = null;
		LoanList alreadyOrdered = new LoanList(new LinkedList<Loan>());
		int investedNotes = 0;
		for (int t = 0; t < Consts.CIDF_WATCH_ITERATIONS; t++){
			ll = api.retrieveLoanList(contentType, false);
			ll.removeLoanList(alreadyOrdered);
			log.info("t= "+t+" ; "+ll.getLoanCount()+" new loans retrieved");
			if (ll.getLoanCount()>0){
//                 System.out.println("Downloaded loanlist: " + ll.toString());
				ll.filter(filt);
				log.info("Filtered. "+ll.getLoanCount()+" loans left");
//				ll.scoreAllLoans(scorer, Consts.CIDF_MIN_SCORE_CUTOFF);
//				log.info("Scored. "+ll.getLoanCount()+" loans left");
//				ll.sortLoans();
//				log.info("Sorted. After sorting, we have developed the following prioritized list of "+ll.getLoanCount()+" desirable loans.");
				log.info("No scoring or sorting. We have developed the following prioritized list of "+ll.getLoanCount()+" desirable loans.");
				if (ll.getLoanCount()>0){
					log.info(ll);
					ll.setRequestAmounts(Consts.PARTIAL_FRACTION, availableCash);
					String orderRequest = api.formatOrderRequest(ll);
                         if (orderRequest.contains("loanId")){
//					log.info("Will now place order.  Order request: "+orderRequest);
					log.info("Will now call api.placeOrder(). Number of requested loans = " + StringUtils.countMatches(orderRequest, "loanId"));
					String confirmation = api.placeOrder(orderRequest, Consts.JSON);
//					log.info("Confirmation: "+confirmation);
					List<OrderConfirmation> ocList = api.parseOrderConfirmation(confirmation, Consts.JSON);
					for (OrderConfirmation oc : ocList){
                           log.info(oc.toString());
						if (oc.getInvestedAmount()>0){
							availableCash-=oc.getInvestedAmount();
							investedNotes++;
							alreadyOrdered.addLoan(ll.getLoanFromID(oc.getLoanID()));
						}
					}
				}
			}
		}
     }
		log.info("Run completed. $ "+(purchaseTarget-availableCash)+" spent in "+investedNotes+" notes.");
	}
}