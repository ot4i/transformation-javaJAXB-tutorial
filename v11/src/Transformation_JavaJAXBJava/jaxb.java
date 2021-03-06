/* 
 * Sample program for use with Product          
 *  ProgIds: 5724-J06 5724-J05 5724-J04 5697-J09 5655-M74 5655-M75 5648-C63 
 *  (C) Copyright IBM Corporation 2014.                      
 * All Rights Reserved * Licensed Materials - Property of IBM 
 * 
 * This sample program is provided AS IS and may be used, executed, 
 * copied and modified without royalty payment by customer 
 * 
 * (a) for its own instruction and study, 
 * (b) in order to develop applications designed to run with an IBM 
 *     WebSphere product, either for customer's own internal use or for 
 *     redistribution by customer, as part of such an application, in 
 *     customer's own products. 
 */

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import org.w3c.dom.Document;
import com.example.jaxb.SaleEnvelope;
import com.example.jaxb.SaleEnvelope.SaleList;
import com.example.jaxb.SaleEnvelope.SaleList.Invoice;
import com.example.jaxb.SaleEnvelope.SaleList.Invoice.Item;
import com.example.jaxb.SaleEnvelopeA;
import com.example.jaxb.SaleEnvelopeA.SaleListA;
import com.example.jaxb.SaleEnvelopeA.SaleListA.Statement;
import com.example.jaxb.SaleEnvelopeA.SaleListA.Statement.Amount;
import com.example.jaxb.SaleEnvelopeA.SaleListA.Statement.Customer;
import com.example.jaxb.SaleEnvelopeA.SaleListA.Statement.Purchases;
import com.example.jaxb.SaleEnvelopeA.SaleListA.Statement.Purchases.Article;
import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;
import com.ibm.broker.plugin.MbXMLNSC;

import java.math.BigDecimal;
import java.util.List;

public class jaxb extends MbJavaComputeNode {

	protected static JAXBContext jaxbContext = null;
	protected static double costMultipler = 1.6;

	public void onInitialize() throws MbException {
		try {
			// TODO Update context path 'com.example.jaxb' to be the package of your
			// Java object classes that were generated by a Java Architecture for XML
			// Binding (JAXB) binding compiler  
			jaxbContext = JAXBContext.newInstance("com.example.jaxb");
		} catch (JAXBException e) {
			// This exception will cause the deploy of this Java compute node to fail
			//  Typical cause is the JAXB package above is not available
			throw new MbUserException(this, "onInitialize()", "", "",
					e.toString(), null);
		}
	}

	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		MbOutputTerminal out = getOutputTerminal("out");

		// obtain the input message data
		MbMessage inMessage = inAssembly.getMessage();

		// create a new empty output message
		MbMessage outMessage = new MbMessage();
		MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly,
				outMessage);

		// optionally copy input message headers to the new output
		copyMessageHeaders(inMessage, outMessage);

		try {
			// unmarshal the input message data from the Broker tree into your Java object classes   
			Object inMsgJavaObj = jaxbContext.createUnmarshaller().unmarshal(
					inMessage.getDOMDocument());

			// ----------------------------------------------------------
			// Add user code below to build the new output data by updating 
			// your Java objects or building new Java objects

			SaleEnvelope saleEnvelope = (SaleEnvelope) inMsgJavaObj;
			SaleListA saleListOut = new SaleListA();

			List<SaleList> inSaleList = saleEnvelope.getSaleList();
			// for each SaleList element
			for(SaleList inSale: inSaleList){
				List<Invoice> inInvoices = inSale.getInvoice();
				// for each Invoice element
				for(Invoice inInv: inInvoices){
					// create a new statement
					Statement outStat = new Statement();
					outStat.setStyle("Full");
					outStat.setType("Monthly");
					Customer outCust = new Customer();
					outCust.setInitials(inInv.getInitial().get(0) + inInv.getInitial().get(1));
					outCust.setName(inInv.getSurname());
					outCust.setBalance(inInv.getBalance());
					outStat.setCustomer(outCust);

					List<Item> inItems = inInv.getItem();
					Purchases outPurch = new Purchases();
					double total = 0;
					// for each Item element
					for (Item inItem : inItems) {
						// create a new article and calculate the total cost
						double outCost = inItem.getPrice() * costMultipler;
						total += outCost * inItem.getQuantity();
						Article outArtic = new Article();
						outArtic.setCost(new BigDecimal("" + outCost));
						outArtic.setDesc(inItem.getDescription());
						outArtic.setQty(inItem.getQuantity());
						outPurch.getArticle().add(outArtic);
					}
					outStat.setPurchases(outPurch);
					Amount outAmount = new Amount();
					outAmount.setContent("" + total);
					outAmount.setCurrency(inInv.getCurrency());
					outStat.setAmount(outAmount);
					
					saleListOut.getStatement().add(outStat);
				}
			}
			
			// put the message in SaleEnvelopeA and push it
			SaleEnvelopeA saleEnvelopeOut = new SaleEnvelopeA();
			saleEnvelopeOut.setSaleListA(saleListOut);
			Object outMsgJavaObj = saleEnvelopeOut;
			// End of user Java object processing
			// ----------------------------------------------------------

			Document outDocument = outMessage
					.createDOMDocument(MbXMLNSC.PARSER_NAME);
			// marshal the new or updated output Java object class into the Broker tree
			jaxbContext.createMarshaller().marshal(outMsgJavaObj, outDocument);

			// The following should only be changed if not propagating message to 
			// the node's 'out' terminal
			out.propagate(outAssembly);
		} catch (JAXBException e) {
			// Example Exception handling	
			throw new MbUserException(this, "evaluate()", "", "", e.toString(),
					null);
		} finally {
			// clear the outMessage even if there's an exception
			outMessage.clearMessage();
		}
	}

	public void copyMessageHeaders(MbMessage inMessage, MbMessage outMessage)
			throws MbException {
		MbElement outRoot = outMessage.getRootElement();

		// iterate though the headers starting with the first child of the root
		// element and stopping before the last child (message body)
		MbElement header = inMessage.getRootElement().getFirstChild();
		while (header != null && header.getNextSibling() != null) {
			// copy the header and add it to the out message
			outRoot.addAsLastChild(header.copy());
			// move along to next header
			header = header.getNextSibling();
		}
	}

}
