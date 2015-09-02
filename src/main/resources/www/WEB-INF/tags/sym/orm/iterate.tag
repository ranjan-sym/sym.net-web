<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@attribute name="items"%>
<%@variable name-given="item" scope="NESTED" %>
<c:forEach var="o" items="${items}">
  <c:set var="item" value="${o}" scope="page"/>
  <jsp:doBody />
</c:forEach>