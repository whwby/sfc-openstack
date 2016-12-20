/*
 * Copyright (c) 2015 who Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.sfc.sfc_openstack.provider;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;

import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.SfcOpenstackTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by One_Homeway on 2016/12/5.
 */
public class SfcOpenstackProvider {
    private static final Logger LOG = LoggerFactory.getLogger(SfcOpenstackProvider.class);

    //md-sal service provider
    private  static DataBroker dataBroker = null;
    private  RpcProviderRegistry rpcRegistry = null;

    private BindingAwareBroker.RpcRegistration<SfcOpenstackTestService> rpcService = null;

    public SfcOpenstackProvider() {
        LOG.warn("sfc openstack Session inst Intiated.");
    }

    public void init() {
        LOG.info("sfc openstack Session Intiated.");
        rpcService = rpcRegistry.addRpcImplementation(SfcOpenstackTestService.class, new SfcOpenstackRpc(dataBroker));
    }
    public void close() {
        LOG.info("sfc openstack Session closed.");
        if (rpcService != null) {
            rpcService.close();
        }
    }

}
