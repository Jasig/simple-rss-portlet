<%@ page contentType="text/html" isELIgnored="false" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="portlet" uri="http://java.sun.com/portlet" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jstl/fmt" %>
<%@ taglib prefix="rs" uri="http://www.jasig.org/resource-server" %>


<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.LinkedHashMap" %>

<%@ page import="au.edu.anu.portal.portlets.rss.utils.Constants" %>

<portlet:defineObjects /> 
<fmt:setBundle basename="au.edu.anu.portal.portlets.rss.utils.messages" />

<link type="text/css" rel="stylesheet"  href="<%=request.getContextPath()%>/css/simple-rss-portlet.css" />

<div class="simple-rss-portlet">

	<div class="simple-rss-portlet-form">
				
		<c:if test="${not empty errorMessage}">
			<div class="portlet-msg-error"><p>${errorMessage}</p></div>
		</c:if>
			
		
		<form method="POST" action="<portlet:actionURL/>" id="<portlet:namespace/>_config">
		
			<p><fmt:message key="config.portlet.title" /></p>
			<input type="text" name="portletTitle" value="${configuredPortletTitle}" size="25"/>
			
			<p><fmt:message key="config.portlet.maxitems" /></p>
			<input type="text" name="maxItems" value="${configuredMaxItems}" size="5"/>
			
			<p><fmt:message key="config.portlet.url" /></p>
			<input type="text" name="feedUrl" value="${configuredFeedUrl}" size="60" />
			
			<p>
	 			<input type="submit" value="<fmt:message key='config.button.submit' />">
			</p>
		</form>
	</div>
	
</div>