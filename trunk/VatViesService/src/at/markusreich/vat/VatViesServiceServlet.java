package at.markusreich.vat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringEscapeUtils;
import org.datanucleus.util.StringUtils;

@SuppressWarnings("serial")
public class VatViesServiceServlet extends HttpServlet {
	private static final String template = "<soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/' xmlns:urn='urn:ec.europa.eu:taxud:vies:services:checkVat:types'><soapenv:Header/><soapenv:Body><urn:checkVatApprox><urn:countryCode>${countryCode}</urn:countryCode><urn:vatNumber>${vatNumber}</urn:vatNumber><urn:requesterCountryCode>${requesterCountryCode}</urn:requesterCountryCode><urn:requesterVatNumber>${requesterVatNumber}</urn:requesterVatNumber></urn:checkVatApprox></soapenv:Body></soapenv:Envelope>";
	
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String requester = req.getParameter("requester");
		String uid = req.getParameter("uid");
		String format = req.getParameter("format");
		if("html".equals(format)) {			
			resp.setContentType("text/html");
			resp.setCharacterEncoding("utf-8");
			resp.getWriter().println("<html>");
			resp.getWriter().println("<head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'></head>");	
			resp.getWriter().println("<body>");
			if(requester==null || uid==null) {
				resp.getWriter().println("<h1>GET Parameter requester and uid are obligatory!</h1>");
			} else {
				try {
					Result result = checkUID(uid, requester);		
					resp.getWriter().println(result.valid + "<br />");
					resp.getWriter().println(result.name + "<br />");
					resp.getWriter().println(result.address + "<br />");
					resp.getWriter().println(result.id + "<br />");
					resp.getWriter().println(result.date + "<br />");					
				} catch (Exception ex) {
					resp.getWriter().println("<h1>" + ex.toString() + "</h1>");
				}
			}		
			resp.getWriter().println("</body>");
			resp.getWriter().println("</html>");
		} else {
			resp.setContentType("application/xml");
			resp.setCharacterEncoding("utf-8");
			resp.getWriter().println("<?xml version='1.0' encoding='UTF-8'?>");		
			if(requester==null || uid==null) {
				resp.getWriter().println("<error>GET Parameter requester and uid are obligatory!</error>");
			} else {
				try {
					Result result = checkUID(uid, requester);		
					resp.getWriter().println("<result>");
					resp.getWriter().println("<valid>" + result.valid + "</valid>");
					resp.getWriter().println("<name>" + StringEscapeUtils.escapeXml(result.name) + "</name>");
					resp.getWriter().println("<address>" + StringEscapeUtils.escapeXml(result.address) + "</address>");
					resp.getWriter().println("<requestid>" + result.id + "</requestid>");
					resp.getWriter().println("<requestdate>" + result.date + "</requestdate>");
					resp.getWriter().println("</result>");
					
				} catch (Exception ex) {
					resp.getWriter().println("<error>" + ex.toString() + "</error>");
				}
			}		
		}
	}	
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	public static Result checkUID(String uid, String requester) throws Exception {
		Result result = new Result();

		// Construct data
		String envelope = template.replace("${vatNumber}", uid.toUpperCase().substring(2)).replace("${countryCode}", uid.toUpperCase().substring(0, 2)).replace("${requesterVatNumber}", requester.toUpperCase().substring(2))
				.replace("${requesterCountryCode}", requester.toUpperCase().substring(0, 2));

		// Send data
		URL url = new URL("http://ec.europa.eu/taxation_customs/vies/services/checkVatService");
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/soap+xml; charset=UTF-8;action=\"checkVatService\"");
		OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
		wr.write(envelope);
		wr.flush();

		// Get the response
		BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
		StringBuffer sb = new StringBuffer();
		String line;
		while ((line = rd.readLine()) != null) {
			sb.append(line);
		}
		
		String resultString = StringEscapeUtils.unescapeHtml4(sb.toString());
		result.valid = Boolean.valueOf(getValue(resultString, "valid"));
		if(result.valid) {
			result.name = getValue(resultString.replace("\n", " "), "traderName");
			result.address = getValue(resultString.replace("\n", ", "), "traderAddress");
			result.id = getValue(resultString, "requestIdentifier");
			result.date = getValue(resultString, "requestDate");
		}			
		
		wr.close();
		rd.close();

		return result;
	}

	private static class Result {
		public boolean valid = false;
		public String name;
		public String address;
		public String id;
		public String date;
	}

	private static String getValue(String text, String tag) {
		Pattern p = Pattern.compile("<" + tag + ">(.*)</" + tag + ">");
		Matcher m = p.matcher(text);
		return m.find() && m.group(1) != null ? m.group(1) : "";
	}
}
