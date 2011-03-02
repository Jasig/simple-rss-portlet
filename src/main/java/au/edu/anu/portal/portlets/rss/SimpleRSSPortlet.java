package au.edu.anu.portal.portlets.rss;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.PortletMode;
import javax.portlet.PortletModeException;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.ReadOnlyException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ValidatorException;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.syndication.feed.synd.SyndFeed;

import au.edu.anu.portal.portlets.rss.utils.Constants;
import au.edu.anu.portal.portlets.rss.utils.Messages;


/**
 * SimpleRssPortlet
 * 
 * This is the portlet class.
 * 
 * @author Steve Swinsburg (steve.swinsburg@anu.edu.au)
 *
 */
public class SimpleRSSPortlet extends GenericPortlet{

	private final Log log = LogFactory.getLog(getClass().getName());
	
	// pages
	private String viewUrl;
	private String editUrl;
	private String errorUrl;
	
	//cache
	private CacheManager cacheManager;
	private Cache feedCache;
	private Cache imageCache;
	
	private final String FEED_CACHE_NAME = "au.edu.anu.portal.portlets.cache.SimpleRSSPortletCache.feed";
	private final String IMAGE_CACHE_NAME = "au.edu.anu.portal.portlets.cache.SimpleRSSPortletCache.images";
	
	public void init(PortletConfig config) throws PortletException {	   
	   super.init(config);
	   log.info("Simple RSS Portlet init()");
	   
	   //pages
	   viewUrl = config.getInitParameter("viewUrl");
	   editUrl = config.getInitParameter("editUrl");
	   errorUrl = config.getInitParameter("errorUrl");

	   //setup cache
	   cacheManager = new CacheManager();
	   feedCache = cacheManager.getCache(FEED_CACHE_NAME);
	   imageCache = cacheManager.getCache(IMAGE_CACHE_NAME);
	}
	
	/**
	 * Delegate to appropriate PortletMode.
	 */
	protected void doDispatch(RenderRequest request, RenderResponse response) throws PortletException, IOException {
		log.info("Simple RSS doDispatch()");

		if (StringUtils.equalsIgnoreCase(request.getPortletMode().toString(), "CONFIG")) {
			doConfig(request, response);
		}
		else {
			super.doDispatch(request, response);
		}
	}
	
	
	/**
	 * Render the main view
	 */
	protected void doView(RenderRequest request, RenderResponse response) throws PortletException, IOException {
		log.info("Simple RSS doView()");
		
		//get feed data
		SyndFeed feed = getFeedContent(request, response);
		
		//catch - errors already handled
		if(feed == null) {
			return;
		}
		
		//get the images that are associated with the entries in this feed
		Map<String,String> images = getEntryImages(feed, request, response);
		
		//get max items (subtract 1 since it will be used in a 0 based index)
		int maxItems = getConfiguredMaxItems(request) - 1;
		
		request.setAttribute("SyndFeed", feed);
		request.setAttribute("EntryImages", images);
		request.setAttribute("maxItems", maxItems);
		
		dispatch(request, response, viewUrl);
	}	
	
	/**
	 * Custom mode handler for EDIT view
	 */
	protected void doEdit(RenderRequest request, RenderResponse response) throws PortletException, IOException {
		log.info("Simple RSS doEdit()");

		//get preferences
		request.setAttribute("configuredPortletTitle", getConfiguredPortletTitle(request));
		request.setAttribute("configuredFeedUrl", getConfiguredFeedUrl(request));
		request.setAttribute("configuredMaxItems", getConfiguredMaxItems(request));
		
		//get any error message that is in the request and pass it on
		request.setAttribute("errorMessage", request.getParameter("errorMessage"));
		
		dispatch(request, response, editUrl);
	}
	
	/**
	 * Custom mode handler for CONFIG view
	 * Identical to EDIT mode.
	 */
	protected void doConfig(RenderRequest request, RenderResponse response) throws PortletException, IOException {
		log.info("Simple RSS doConfig()");
		doEdit(request,response);
	}
	
	/**
	 * Process any portlet actions. At this stage they are all from the submission of the CONFIG mode.
	 */
	public void processAction(ActionRequest request, ActionResponse response) {
		log.info("Simple RSS processAction()");
		
		//this handles both EDIT and CONFIG modes in exactly the same way.
		//if we need to split, check PortletMode.
		
		boolean success = true;
		//get prefs and submitted values
		PortletPreferences prefs = request.getPreferences();
		String portletTitle = request.getParameter("portletTitle");
		String maxItems = request.getParameter("maxItems");
		String feedUrl = request.getParameter("feedUrl");
		
		//portlet title could be blank, set to default
		if(StringUtils.isBlank(portletTitle)){
			portletTitle=Constants.PORTLET_TITLE_DEFAULT;
		}
		
		//check not readonly
		try {
			prefs.setValue("portlet_title", portletTitle);
			prefs.setValue("feed_url", feedUrl);
			prefs.setValue("max_items", maxItems);
		} catch (ReadOnlyException e) {
			success = false;
			response.setRenderParameter("errorMessage", Messages.getString("error.form.readonly.error"));
			log.error(e);
		}
		
		//validate and save
		if(success) {
			try {
				prefs.store();
				response.setPortletMode(PortletMode.VIEW);
				
			} catch (ValidatorException e) {
				response.setRenderParameter("errorMessage", e.getMessage());
				log.error(e);
			} catch (IOException e) {
				response.setRenderParameter("errorMessage", Messages.getString("error.form.save.error"));
				log.error(e);
			} catch (PortletModeException e) {
				e.printStackTrace();
			}
		}
		
		
	}
	
	
	/**
	 * Get the feed content
	 * @param request
	 * @param response
	 * @return Map of params or null if any required data is missing
	 */
	private SyndFeed getFeedContent(RenderRequest request, RenderResponse response) {
		
		SyndFeed feed;
		
		//check cache, otherwise get fresh
		//we use the feedUrl as the cacheKey
		String feedUrl = getConfiguredFeedUrl(request);
		if(StringUtils.isBlank(feedUrl)) {
			log.error("No feed URL configured");
			doError("error.no.config", "error.heading.config", request, response);
			return null;
		}
		
		String cacheKey = feedUrl;
		
		Element element = feedCache.get(cacheKey);
		if(element != null) {
			log.info("Fetching data from cache for: " + cacheKey);
			feed = (SyndFeed) element.getObjectValue();
			if(feed == null) {
				log.warn("Cache data invalid, attempting a refresh...");
				feed = getRemoteFeed(feedUrl, request, response);
			}
		} else {
		
			//get from remote
			feed = getRemoteFeed(feedUrl, request, response);
		}
		
		return feed;
	}
	
	/**
	 * Helper to get the remote feed data and cache it
	 * @param feedUrl
	 * @param request
	 * @param response
	 * @return
	 */
	private SyndFeed getRemoteFeed(String feedUrl, RenderRequest request, RenderResponse response) {
		
		//get feed data
		SyndFeed feed = new FeedParser().parseFeed(feedUrl);
		if(feed == null) {
			log.error("No data was returned from remote server.");
			doError("error.no.remote.data", "error.heading.general", request, response);
			return null;
		}
		
		//cache the data,
		log.info("Adding data to cache for: " + feedUrl);
		feedCache.put(new Element(feedUrl, feed));
		
		return feed;
	}
	
	
	/**
	 * Helper for extracting the images associated with the entries in a feed, and cache their URLs
	 * @param feed
	 * @param request
	 * @param response
	 * @return
	 */
	private Map<String,String> getEntryImages(SyndFeed feed, RenderRequest request, RenderResponse response) {
		
		Map<String,String> images;
		
		String feedUrl = getConfiguredFeedUrl(request);
		
		//check cache
		Element element = imageCache.get(feedUrl);
		if(element != null) {
			log.info("Fetching data from image cache for: " + feedUrl);
			return (Map<String,String>) element.getObjectValue();
		} else {
		
			//parse the feed
			images = FeedParser.parseEntryImages(feed);
		}
		
		//cache the data,
		log.info("Adding data to image cache for: " + feedUrl);
		imageCache.put(new Element(feedUrl, images));
		return images;
		
	}
	
	
	
	/**
	 * Get the preferred portlet title if set, or default from Constants
	 * @param request
	 * @return
	 */
	private String getConfiguredPortletTitle(RenderRequest request) {
		PortletPreferences pref = request.getPreferences();
		return pref.getValue("portlet_title", Constants.PORTLET_TITLE_DEFAULT);
	}
	
	/**
	 * Get the preferred portlet height if set, or default from Constants
	 * @param request
	 * @return
	 */
	private String getConfiguredFeedUrl(RenderRequest request) {
	      PortletPreferences pref = request.getPreferences();
	      return pref.getValue("feed_url", Constants.FEED_URL_DEFAULT);
	}
	
	/**
	 * Get the preferred max number of items, or default from Constants
	 * @param request
	 * @return
	 */
	private int getConfiguredMaxItems(RenderRequest request) {
	      PortletPreferences pref = request.getPreferences();
	      return Integer.valueOf(pref.getValue("max_items", Integer.toString(Constants.MAX_ITEMS)));
	}
	
	
	
	/**
	 * Override GenericPortlet.getTitle() to use the preferred title for the portlet instead
	 */
	@Override
	protected String getTitle(RenderRequest request) {
		return getConfiguredPortletTitle(request);
	}
	

	/**
	 * Helper to handle error messages
	 * @param messageKey	Message bundle key
	 * @param headingKey	optional error heading message bundle key, if not specified, the general one is used
	 * @param request
	 * @param response
	 */
	private void doError(String messageKey, String headingKey, RenderRequest request, RenderResponse response){
		
		//message
		request.setAttribute("errorMessage", Messages.getString(messageKey));
		
		//optional heading
		if(StringUtils.isNotBlank(headingKey)){
			request.setAttribute("errorHeading", Messages.getString(headingKey));
		} else {
			request.setAttribute("errorHeading", Messages.getString("error.heading.general"));
		}
		
		//dispatch
		try {
			dispatch(request, response, errorUrl);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Dispatch to a JSP or servlet
	 * @param request
	 * @param response
	 * @param path
	 * @throws PortletException
	 * @throws IOException
	 */
	protected void dispatch(RenderRequest request, RenderResponse response, String path)throws PortletException, IOException {
		response.setContentType("text/html"); 
		PortletRequestDispatcher dispatcher = getPortletContext().getRequestDispatcher(path);
		dispatcher.include(request, response);
	}

	
	
	public void destroy() {
		log.info("destroy()");
		cacheManager.shutdown();
	}
	
	
}