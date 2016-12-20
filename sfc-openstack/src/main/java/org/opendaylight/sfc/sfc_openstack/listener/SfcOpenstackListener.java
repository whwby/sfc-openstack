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
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.SfcEntries;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.SfcMatch;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.SfpEntries;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.sfp.entries.sfp.sf.entries.SfpSfEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.entries.SfcEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.sfp.entries.Source;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.sfp.entries.Destination;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.sfp.entries.SfpSfEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.opendaylight.sfc.sfc_openstack.SfcOpenstackUtil;
import org.opendaylight.sfc.sfc_openstack.SfcNshHeader;


public class SfcOpenstackListener extends SfcOpenstackAbstractListener<SfcEntry> {
    private static final Logger LOG = LoggerFactory.getLogger(SfcOpenstackListener.class);
    private final DataBroker dataBroker;
    private ListenerRegistration<SfcOpenstackListener> listenerRegistration;

    public SfcOpenstackListener(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public void init() {
        LOG.info("Initializing openstack 0 listener...");
        registerListeners();
    }
    public void close() throws Exception {
        LOG.info("Closing listener 0 ...");
        if(listenerRegistration != null) {
            listenerRegistration.close();
        }
    }

    private void registerListeners() {
        LOG.info("i am in Initializing openstack registerListeners");
        final DataTreeIdentifier<SfcEntry> treeId = new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(SfcEntries.class).child(SfcEntry.class));
        listenerRegistration = dataBroker.registerDataTreeChangeListener(treeId,this);
    }

    @Override
    public void add(SfcEntry sfcEntry) {
        if (sfcEntry != null) {
            LOG.info("Adding sfc entry: {}", sfcEntry.getName());
            //create flow tables

            SfpEntries sfpEntries = sfcEntry.getSfpEntries();
            Source source = sfpEntries.getSource();
            Destination destination = sfpEntries.getDestination();
            SfpSfEntries sfpSfEntries = sfpEntries.getSfpSfEntries();

            SfpSfEntry sfpSfEntry = sfpSfEntries.getSfpSfEntry().get(1);
            Long nsp = 60L + sfcEntry.getSfcNsp();

            String sBridgeId = SfcOpenstackUtil.getBridgeID(source.getBridgeName(),source.getNodeIp().toString());
            String sfBridgeId = SfcOpenstackUtil.getBridgeID(sfpSfEntry.getBridgeName(),sfpSfEntry.getNodeIp().toString());
            String sSfcBridgeId = SfcOpenstackUtil.getBridgeID("br-sfc",source.getNodeIp().toString());
            String sfSfcBridgeId = SfcOpenstackUtil.getBridgeID("br-sfc",sfpSfEntry.getNodeIp().toString());
            String dBridgeId = SfcOpenstackUtil.getBridgeID(destination.getBridgeName(),destination.getNodeIp().toString());
            String dSfcBridgeId = SfcOpenstackUtil.getBridgeID("br-sfc",destination.getNodeIp().toString());
            //suppose only a sf created

            createSource2SF(source,sfpSfEntry,nsp,compareLocation1(source,sfpSfEntry),sfcEntry.getSfcMatch());
            createSF2Destination(sfpSfEntry,destination,nsp,compareLocation1(source,sfpSfEntry));

            //create other flows in br-int and br-sfc

            SfcOpenstackUtil.createOtherIntFlows(sBridgeId,(short)0);
            SfcOpenstackUtil.createOtherSfcFlows(sSfcBridgeId,(short)0);

            if (sfBridgeId != sBridgeId) {
                SfcOpenstackUtil.createOtherIntFlows(sfBridgeId,(short)0);
                SfcOpenstackUtil.createOtherSfcFlows(sfSfcBridgeId,(short)0);
            }
            if (dBridgeId != sBridgeId && dBridgeId != sBridgeId) {
                SfcOpenstackUtil.createOtherIntFlows(dBridgeId,(short)0);
                SfcOpenstackUtil.createOtherSfcFlows(dSfcBridgeId,(short)0);
            }



        }
    }



    @Override
    public void remove(SfcEntry sfcEntry) {
        if (sfcEntry != null) {
            LOG.info("Deleting Sfc entry: {}", sfcEntry.getName());
            //delete flow tables
        }
    }

    @Override
    protected void update(SfcEntry orignalSfc, SfcEntry updateSfc) {


    }


    private void createSource2SF(Source source, SfpSfEntry sfpSfEntry , Long nsp, Boolean sameNode,
                                        SfcMatch sfcMatch) {
        String sBridgeId = SfcOpenstackUtil.getBridgeID(source.getBridgeName(),source.getNodeIp().toString());
        String sfBridgeId = SfcOpenstackUtil.getBridgeID(sfpSfEntry.getBridgeName(),sfpSfEntry.getNodeIp().toString());
        String sSfcBridgeId = SfcOpenstackUtil.getBridgeID("br-sfc",source.getNodeIp().toString());
        String sfSfcBridgeId = SfcOpenstackUtil.getBridgeID("br-sfc",sfpSfEntry.getNodeIp().toString());
        if (sameNode) {
            Match  match = SfcOpenstackUtil.createMatch(sfcMatch.getSrcIp(), sfcMatch.getDstIp());
            Long sfport = SfcOpenstackUtil.getSfcPort(sfpSfEntry.getBridgeName(),
                    sfpSfEntry.getNodeIp().toString(),
                    sfpSfEntry.getPortName());
            SfcNshHeader sfcNshHeader = SfcOpenstackUtil.createSfcNshHeader(nsp,
                    (short)255,
                    sfpSfEntry.getSfMac().toString());

            LOG.info("IN openstack listener ADD step1 is: {} <> {}" + sBridgeId,sfport);
            SfcOpenstackUtil.createFirstFlowInSameNode(sBridgeId,(short)0,match,sfcNshHeader,sfport);
        }
        else {
            //first in node1,create br-int flow
            Match  match = SfcOpenstackUtil.createMatch(sfcMatch.getSrcIp(),
                    sfcMatch.getDstIp());
            Long port = SfcOpenstackUtil.getSfcPort(source.getBridgeName(),
                    source.getNodeIp().toString(),
                    "int2sfc");
            SfcNshHeader sfcNshHeader = SfcOpenstackUtil.createSfcNshHeader(nsp,
                    (short)255,null);
            LOG.info("IN openstack listener ADD step21 is: {} <> {}" + sBridgeId,port);
            SfcOpenstackUtil.createFirstFlowInDifferentNode(sBridgeId,(short)0,match,sfcNshHeader,port);

            //create br-sfc flow
            Long sfcportIn = SfcOpenstackUtil.getSfcPort("br-sfc",
                    source.getNodeIp().toString(), "sfc2int");
            Long sfcportOut = SfcOpenstackUtil.getSfcPort("br-sfc",
                    source.getNodeIp().toString(), "spt6633");

            String inPortConnId = sSfcBridgeId + ":" +  sfcportIn;
            Match  sfcmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)255, inPortConnId);
            SfcOpenstackUtil.createSfcFlows(sBridgeId,(short)0, sfcmatch, sfcportOut);


            //second in node2, create br-sfc flow
            Long sfcportIn2 = SfcOpenstackUtil.getSfcPort("br-sfc",
                    sfpSfEntry.getNodeIp().toString(), "sfc2int");
            Long sfcportOut2 = SfcOpenstackUtil.getSfcPort("br-sfc",
                    sfpSfEntry.getNodeIp().toString(), "spt6633");

            String inPortConnId2 = sfSfcBridgeId + ":" +  sfcportOut2;
            Match  sfcmatch2 = SfcOpenstackUtil.createNshMatch(nsp,(short)255, inPortConnId2);
            SfcOpenstackUtil.createSfcFlows(sfBridgeId,(short)0, sfcmatch2, sfcportIn2);

            //create br-int flow
            Long sfportIn = SfcOpenstackUtil.getSfcPort(sfpSfEntry.getBridgeName(),
                    sfpSfEntry.getNodeIp().toString(), "int2sfc");
            String inPortConnId3 = sfBridgeId + ":" +  sfportIn;
            Match  sfmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)255, inPortConnId3);
            Long sfport = SfcOpenstackUtil.getSfcPort(sfpSfEntry.getBridgeName(),
                    sfpSfEntry.getNodeIp().toString(), sfpSfEntry.getPortName());

            SfcNshHeader sfcNshHeader2 = SfcOpenstackUtil.createSfcNshHeader(nsp,
                    (short)255, sfpSfEntry.getSfMac().toString());

            LOG.info("IN openstack listener ADD step22 is: {} <> {}" + sfBridgeId,sfport);
            SfcOpenstackUtil.createFlowToVNF(sfBridgeId,(short)0,sfmatch,sfcNshHeader2, sfport);
        }

    }

    private void createSF2Destination(SfpSfEntry sfpSfEntry, Destination destination, Long nsp, Boolean sameNode) {
        String sfBridgeId = SfcOpenstackUtil.getBridgeID(sfpSfEntry.getBridgeName(),sfpSfEntry.getNodeIp().toString());
        String dBridgeId = SfcOpenstackUtil.getBridgeID(destination.getBridgeName(),destination.getNodeIp().toString());
        String sfSfcBridgeId = SfcOpenstackUtil.getBridgeID("br-sfc",sfpSfEntry.getNodeIp().toString());
        String dSfcBridgeId = SfcOpenstackUtil.getBridgeID("br-sfc",destination.getNodeIp().toString());
        if (sameNode) {
            Long sfport = SfcOpenstackUtil.getSfcPort(sfpSfEntry.getBridgeName(),
                    sfpSfEntry.getNodeIp().toString(), sfpSfEntry.getPortName());
            String inPortConnId = dBridgeId + ":" +  sfport;
            Match  match = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId);

            Long dport = SfcOpenstackUtil.getSfcPort(destination.getBridgeName(),
                    destination.getNodeIp().toString(),
                    destination.getPortName());
            LOG.info("IN openstack listener ADD step3 is: {} <> {}" + dBridgeId,sfport);
            SfcOpenstackUtil.createFlowFromVNF(dBridgeId,(short)0,match,dport);
        }
        else {
            //first step1, create br-int flow
            Long sfport = SfcOpenstackUtil.getSfcPort(sfpSfEntry.getBridgeName(),
                    sfpSfEntry.getNodeIp().toString(), sfpSfEntry.getPortName());
            String inPortConnId = sfBridgeId + ":" +  sfport;
            Match  sfmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId);
            Long port = SfcOpenstackUtil.getSfcPort(sfpSfEntry.getBridgeName(),
                    sfpSfEntry.getNodeIp().toString(),
                    "int2sfc");

            LOG.info("IN openstack listener ADD step41 is: {} <> {}" + sfBridgeId,sfport);
            SfcOpenstackUtil.createFlowToNormal(sfBridgeId,(short)0,sfmatch,port);

            //create br-sfc flow
            Long sfcportIn = SfcOpenstackUtil.getSfcPort("br-sfc",
                    sfpSfEntry.getNodeIp().toString(), "sfc2int");
            Long sfcportOut = SfcOpenstackUtil.getSfcPort("br-sfc",
                    sfpSfEntry.getNodeIp().toString(), "spt6633");

            String inPortConnId2 = sfSfcBridgeId + ":" +  sfcportIn;
            Match  sfcmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId2);
            SfcOpenstackUtil.createSfcFlows(sfBridgeId,(short)0, sfcmatch, sfcportOut);


            //first step2, create br-sfc flow
            Long sfcportIn2 = SfcOpenstackUtil.getSfcPort("br-sfc",
                    destination.getNodeIp().toString(), "sfc2int");
            Long sfcportOut2 = SfcOpenstackUtil.getSfcPort("br-sfc",
                    destination.getNodeIp().toString(), "spt6633");

            String inPortConnId3 = dSfcBridgeId + ":" +  sfcportOut2;
            Match  sfcmatch2 = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId3);
            SfcOpenstackUtil.createSfcFlows(sfBridgeId,(short)0, sfcmatch2, sfcportIn2);

            //create br-int flow
            Long sfcport = SfcOpenstackUtil.getSfcPort(destination.getBridgeName(),
                    destination.getNodeIp().toString(), "int2sfc");
            String inPortConnId4 = dBridgeId + ":" +  sfcport;
            Match  dmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId4);
            Long dport = SfcOpenstackUtil.getSfcPort(destination.getBridgeName(),
                    destination.getNodeIp().toString(),
                    destination.getPortName());

            LOG.info("IN openstack listener ADD step42 is: {} <> {}" + dBridgeId,dport);
            SfcOpenstackUtil.createFlowFromVNF(dBridgeId,(short)0,dmatch,dport);
        }

    }

    private Boolean compareLocation1(Source source, SfpSfEntry sfpSfEntry) {
        if (source.getBridgeName() == sfpSfEntry.getBridgeName() &&
                source.getNodeIp() == sfpSfEntry.getNodeIp()) {
            return true;
        }
        return false;
    }

    private Boolean compareLocation2(SfpSfEntry sfpSfEntry, Destination destination) {
        if(sfpSfEntry.getBridgeName() == destination.getBridgeName() &&
                sfpSfEntry.getNodeIp() == destination.getNodeIp()) {
            return true;
        }
        return false;
    }

}
