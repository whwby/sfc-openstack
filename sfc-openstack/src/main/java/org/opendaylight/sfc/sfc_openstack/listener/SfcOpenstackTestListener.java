/*
 * Copyright (c) 2015 who Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Created by One_Homeway on 2016/12/2.
 */

package org.opendaylight.sfc.sfc_openstack.listener;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.encap.type.EncapType1;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.sff.base.SfList;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.sff.base.sf.list.SfEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.sff.list.SffEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.SffList;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.encap.type.encap.type1.*;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.opendaylight.sfc.sfc_openstack.SfcOpenstackUtil;
import org.opendaylight.sfc.sfc_openstack.SfcNshHeader;


public class SfcOpenstackTestListener extends SfcOpenstackAbstractListener<SffEntry> {
    private static final Logger LOG = LoggerFactory.getLogger(SfcOpenstackTestListener.class);
    private final DataBroker dataBroker;
    private ListenerRegistration<SfcOpenstackTestListener> listenerRegistration;

    public SfcOpenstackTestListener(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public void init() {
        LOG.info("Initializing openstack listener...");
        registerListeners();
    }
    public void close() throws Exception {
        LOG.info("Closing listener...");
        if(listenerRegistration != null) {
            listenerRegistration.close();
        }
    }

    private void registerListeners() {
        LOG.info("i am in Initializing openstack registerListeners");
        final DataTreeIdentifier<SffEntry> treeId = new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(SffList.class).child(SffEntry.class));
        listenerRegistration = dataBroker.registerDataTreeChangeListener(treeId,this);
    }

    @Override
    public void add(SffEntry sffEntry) {
        if (sffEntry != null) {
            LOG.info("Adding sff entry: {}", sffEntry.getName());
            //create flow tables

            SfList sfList = sffEntry.getSfList();
            SfEntry sfEntry = sfList.getSfEntry().get(0);
            LOG.info("Adding sf entry: {}", sfEntry.toString());
            EncapType1 encapType = sfEntry.getEncapType1();
            String dstMac = "33:11:11:22:22:22";
            Long outport = 3L;
            if (encapType instanceof Ether) {
                Ether ether = (Ether) encapType;
                dstMac = ether.getMac().getValue();

                outport = Long.valueOf(ether.getPortId()).longValue();
                LOG.info("Adding ether dstMac: {}<>{}", dstMac,outport);
            }
            String datapathId = sffEntry.getNode();

            SfcNshHeader sfcNshHeader = new SfcNshHeader()
                    .setNshNsp(66L)
                    .setEncapSrc("11:11:11:22:22:22")
                    .setEncapDst(dstMac)
                    .setNshStartNsi((short)255)
                    .setNshMetaC1(55L)
                    .setNshMetaC2(56L)
                    .setNshMetaC3(57L)
                    .setNshMetaC4(58L);
            String nodeName = "openflow:" + datapathId;
            SfcOpenstackUtil.createClassifierInFlow(nodeName,
                    null,null,sfcNshHeader,outport);
        }
    }

    @Override
    public void remove(SffEntry sffEntry) {
        if (sffEntry != null) {
            LOG.info("Deleting Sff entry: {}", sffEntry.getName());
            //delete flow tables
        }
    }

    @Override
    protected void update(SffEntry orignalSff, SffEntry updateSff) {

        String nodeName = "openflow:" + updateSff.getNode();
        Long outport = 2L;
        if (orignalSff != null) {
            LOG.info("Updating sff entry: {}>>{}>>{}", orignalSff.getName(), updateSff.getNode(),outport);
            if(!compareSff(orignalSff,updateSff)) {
                //delete orignal sff, add update sff.
                SfcOpenstackUtil.createTestFlow(nodeName,outport);
            }
        }
    }

    private boolean compareSff(SffEntry orignalSff, SffEntry updateSff) {
        //compare sff
        return false;
    }
}
