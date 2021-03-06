/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ambari.logsearch.converter;

import org.apache.ambari.logsearch.model.request.impl.CommonSearchRequest;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.solr.core.query.Criteria;
import org.springframework.data.solr.core.query.Query;
import org.springframework.data.solr.core.query.SimpleStringCriteria;

public abstract class AbstractSearchRequestQueryConverter<REQUEST_TYPE extends CommonSearchRequest, QUERY_TYPE extends Query>
  extends AbstractOperationHolderConverter<REQUEST_TYPE, QUERY_TYPE> {

  @Override
  public QUERY_TYPE convert(REQUEST_TYPE request) {
    QUERY_TYPE query = createQuery();
    int page = StringUtils.isNumeric(request.getPage()) ? new Integer(request.getPage()) : 0;
    int pageSize = StringUtils.isNumeric(request.getPageSize()) ? new Integer(request.getPageSize()) : 99999;
    PageRequest pageRequest = new PageRequest(page, pageSize, sort(request));
    query.setPageRequest(pageRequest);
    Criteria criteria = new SimpleStringCriteria("*:*");
    query.addCriteria(criteria);
    return extendSolrQuery(request, query);
  }

  public abstract QUERY_TYPE extendSolrQuery(REQUEST_TYPE request, QUERY_TYPE query);

  public abstract Sort sort(REQUEST_TYPE request);

  public abstract QUERY_TYPE createQuery();
}
