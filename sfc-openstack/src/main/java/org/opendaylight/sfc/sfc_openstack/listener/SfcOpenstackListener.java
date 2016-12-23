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
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.SfcBridge;
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

import java.util.ArrayList;


public class SfcOpenstackListener extends SfcOpenstackAbstractListener<SfcEntry> {

    private static final String INT_2_SFC = "int2sfc";
    private static final String SFC_2_INT = "sfc2int";
    private static final String TUNNEL_POINT = "spt6633";
    private static final Logger LOG = LoggerFactory.getLogger(SfcOpenstackListener.class);
    private final DataBroker dataBroker;
    private ListenerRegistration<SfcOpenstackListener> listenerRegistration;

    private ArrayList<String> flowList = new ArrayList<>();

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
        LOG.info("I am in Initializing openstack registerListeners");
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

            SfpSfEntry sfpSfEntry = sfpSfEntries.getSfpSfEntry().get(0);
            Long nsp = 15L + sfcEntry.getSfcNsp();
            SfcBridge sfcBridge = sfcEntry.getSfcBridge();
            String intBridgeName;
            if(sfcBridge.getConvergeBridgeName()!= null){
                intBridgeName = sfcBridge.getConvergeBridgeName();
            }else {
                intBridgeName = "br-int";
            }

            String sfcBridgeName;
            if (sfcBridge.getTunnelBridgeName() != null) {
                sfcBridgeName = sfcBridge.getTunnelBridgeName();
            }else {
                sfcBridgeName = "br-sfc";
            }

            String sBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,source.getNodeIp());
            String sfBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,sfpSfEntry.getNodeIp());
            String dBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,destination.getNodeIp());

            String sSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,source.getNodeIp());
            String sfSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,sfpSfEntry.getNodeIp());
            String dSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,destination.getNodeIp());
            //suppose only a sf created

            createSource2SF(source,sfpSfEntry,nsp,
                    sBridgeId.equals(sfBridgeId),
                    sfcEntry.getSfcMatch(),
                    sfcBridge);
            createSF2Destination(sfpSfEntry,destination,nsp,
                    sfBridgeId.equals(dBridgeId),
                    sfcBridge);

            //create other flows in br-int and br-sfc
            StringBuffer key = new StringBuffer().append("br-int").append("normal");
            storeFlowKey(key,nsp,sBridgeId);
            SfcOpenstackUtil.createNormalIntFlow(sBridgeId,(short)0,key.toString());

            StringBuffer key1 = new StringBuffer().append("br-sfc").append("drop");
            storeFlowKey(key1,nsp,sSfcBridgeId);
            SfcOpenstackUtil.createDropSfcFlow(sSfcBridgeId,(short)0, key1.toString());

            if (!sfBridgeId.equals(sBridgeId)) {
                storeFlowKey(key,nsp,sfBridgeId);
                SfcOpenstackUtil.createNormalIntFlow(sfBridgeId,(short)0,key.toString());
                storeFlowKey(key1,nsp,sfSfcBridgeId);
                SfcOpenstackUtil.createDropSfcFlow(sfSfcBridgeId,(short)0,key1.toString());
            }
            if (!dBridgeId.equals(sBridgeId) && !dBridgeId.equals(sBridgeId)) {
                storeFlowKey(key,nsp,dBridgeId);
                SfcOpenstackUtil.createNormalIntFlow(dBridgeId,(short)0,key.toString());
                storeFlowKey(key1,nsp,dSfcBridgeId);
                SfcOpenstackUtil.createDropSfcFlow(dSfcBridgeId,(short)0,key1.toString());
            }

        }
    }


    @Override
    public void remove(SfcEntry sfcEntry) {
        if (sfcEntry != null) {
            LOG.info("Deleting Sfc entry: {}", sfcEntry.getName());
            //delete flow table;

            for (String flowKey : flowList) {
                String s[] = flowKey.split("\\|");
                LOG.info("Deleting Sfc entry info: {}>>>{}>>>{}", s[2],s[1],s[0]);
                if (s[1].equals(sfcEntry.getSfcNsp().toString())) {

                    SfcOpenstackUtil.deleteFlow(s[2], s[0]);
                    flowList.remove(flowKey);
                }
            }
        }
    }

    @Override
    protected void update(SfcEntry orignalSfc, SfcEntry updateSfc) {
        LOG.info("Simple update sfcEntry based on different nsp.Updating Sfc entry: {}", updateSfc.getName());
        if(orignalSfc.getSfcNsp().equals(updateSfc.getSfcNsp())) {
            remove(orignalSfc);
            add(updateSfc);
        }
        else {
            LOG.info("No updating for Sfc entry: {}", orignalSfc.getName());
        }

    }


    private void createSource2SF(Source source, SfpSfEntry sfpSfEntry , Long nsp, Boolean sameNode,
                                        SfcMatch sfcMatch,SfcBridge sfcBridge) {
        String intBridgeName;
        if(sfcBridge.getConvergeBridgeName()!= null){
            intBridgeName = sfcBridge.getConvergeBridgeName();
        }else {
            intBridgeName = "br-int";
        }

        String sfcBridgeName;
        if (sfcBridge.getTunnelBridgeName() != null) {
            sfcBridgeName = sfcBridge.getTunnelBridgeName();
        }else {
            sfcBridgeName = "br-sfc";
        }
        String sBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,source.getNodeIp());
        String sfBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,sfpSfEntry.getNodeIp());
        String sSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,source.getNodeIp());
        String sfSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,sfpSfEntry.getNodeIp());
        LOG.info("IN openstack listener ADD step1 createSource2SF  is: {} <> {}" ,sBridgeId,sfBridgeId);
        if (sameNode) {
            Match  match = SfcOpenstackUtil.createMatch(sfcMatch.getSrcIp(), sfcMatch.getDstIp(),
                    sfcMatch.getProtocolName(),sfcMatch.getPort().getValue());
            Long sfport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(),
                    sfpSfEntry.getPortName());
            SfcNshHeader sfcNshHeader = SfcOpenstackUtil.createSfcNshHeader(nsp,
                    (short)255,
                    sfpSfEntry.getSfMac().getValue());

            LOG.info("IN openstack listener ADD step1 is: {} <> {}" , sBridgeId,sfport);
            StringBuffer key = new StringBuffer();
            key.append(source.getPortName()).append(sfpSfEntry.getPortName()).append(nsp).append(255);
            storeFlowKey(key,nsp,sBridgeId);
            SfcOpenstackUtil.createClassifierIntFlow(sBridgeId,(short)0,key.toString(),
                    match,sfcNshHeader,sfport, sameNode);
        }
        else {
            //first in node1,create br-int flow
            Match  match = SfcOpenstackUtil.createMatch(sfcMatch.getSrcIp(),sfcMatch.getDstIp(),
                    sfcMatch.getProtocolName(),sfcMatch.getPort().getValue());
            Long port = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    source.getNodeIp(),
                    INT_2_SFC);
            SfcNshHeader sfcNshHeader = SfcOpenstackUtil.createSfcNshHeader(nsp,
                    (short)255,null);

            LOG.info("IN openstack listener ADD step21 is: {} <> {}" , sBridgeId,port);
            StringBuffer key = new StringBuffer();
            key.append(source.getPortName()).append(INT_2_SFC).append(nsp).append(255);
            storeFlowKey(key,nsp,sBridgeId);
            SfcOpenstackUtil.createClassifierIntFlow(sBridgeId,(short)0,key.toString(),
                    match,sfcNshHeader,port,sameNode);

            //create br-sfc flow
            Long sfcportIn = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    source.getNodeIp(), SFC_2_INT);
            Long sfcportOut = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    source.getNodeIp(), TUNNEL_POINT);

            String inPortConnId = sSfcBridgeId + ":" +  sfcportIn;
            Match  sfcmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)255, inPortConnId);

            StringBuffer key1 = new StringBuffer();
            key1.append(SFC_2_INT).append(TUNNEL_POINT).append(nsp).append(255);
            storeFlowKey(key1,nsp,sSfcBridgeId);
            SfcOpenstackUtil.createOutputSfcFlow(sSfcBridgeId,(short)0, key1.toString(),sfcmatch, sfcportOut);

            //second in node2, create br-sfc flow
            Long sfcportIn2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    sfpSfEntry.getNodeIp(), SFC_2_INT);
            Long sfcportOut2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    sfpSfEntry.getNodeIp(), TUNNEL_POINT);

            String inPortConnId2 = sfSfcBridgeId + ":" +  sfcportOut2;
            Match  sfcmatch2 = SfcOpenstackUtil.createNshMatch(nsp,(short)255, inPortConnId2);

            StringBuffer key2 = new StringBuffer();
            key2.append(TUNNEL_POINT).append(SFC_2_INT).append(nsp).append(255);
            storeFlowKey(key2,nsp,sfSfcBridgeId);
            SfcOpenstackUtil.createOutputSfcFlow(sfSfcBridgeId,(short)0, key2.toString(),sfcmatch2, sfcportIn2);

            //create br-int flow
            Long sfportIn = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(), INT_2_SFC);
            String inPortConnId3 = sfBridgeId + ":" +  sfportIn;
            Match  sfmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)255, inPortConnId3);
            Long sfport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(), sfpSfEntry.getPortName());

            SfcNshHeader sfcNshHeader2 = SfcOpenstackUtil.createSfcNshHeader(nsp,
                    (short)255, sfpSfEntry.getSfMac().getValue());

            LOG.info("IN openstack listener ADD step22 is: {} <> {}" ,sfBridgeId,sfport);
            StringBuffer key3 = new StringBuffer();
            key3.append(INT_2_SFC).append(sfpSfEntry.getPortName()).append(nsp).append(255);
            storeFlowKey(key3,nsp,sfBridgeId);
            SfcOpenstackUtil.createToVNFIntFlow(sfBridgeId,(short)0,key3.toString(),sfmatch,sfcNshHeader2, sfport);
        }
    }

    private void createSF2Destination(SfpSfEntry sfpSfEntry, Destination destination, Long nsp, Boolean sameNode,
                                      SfcBridge sfcBridge) {
        String intBridgeName;
        if(sfcBridge.getConvergeBridgeName()!= null){
            intBridgeName = sfcBridge.getConvergeBridgeName();
        }else {
            intBridgeName = "br-int";
        }

        String sfcBridgeName;
        if (sfcBridge.getTunnelBridgeName() != null) {
            sfcBridgeName = sfcBridge.getTunnelBridgeName();
        }else {
            sfcBridgeName = "br-sfc";
        }
        String sfBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,sfpSfEntry.getNodeIp());
        String dBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,destination.getNodeIp());
        String sfSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,sfpSfEntry.getNodeIp());
        String dSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,destination.getNodeIp());
        if (sameNode) {
            Long sfport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(), sfpSfEntry.getPortName());
            String inPortConnId = dBridgeId + ":" +  sfport;
            Match  match = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId);

            Long dport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    destination.getNodeIp(),
                    destination.getPortName());
            LOG.info("IN openstack listener ADD step3 is: {} <> {}" , dBridgeId,dport);
            StringBuffer key = new StringBuffer();
            key.append(sfpSfEntry.getPortName()).append(destination.getPortName()).append(nsp).append(254);
            storeFlowKey(key,nsp,dBridgeId);
            SfcOpenstackUtil.createToDestinationIntFlow(dBridgeId,(short)0,key.toString(),match,dport);
        }
        else {
            //first step1, create br-int flow
            Long sfport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(), sfpSfEntry.getPortName());
            String inPortConnId = sfBridgeId + ":" +  sfport;
            Match  sfmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId);
            Long port = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(),
                    INT_2_SFC);

            LOG.info("IN openstack listener ADD step41 is: {} <> {}" ,sfBridgeId,port);
            StringBuffer key = new StringBuffer();
            key.append(sfpSfEntry.getPortName()).append(INT_2_SFC).append(nsp).append(254);
            storeFlowKey(key,nsp,sfBridgeId);
            SfcOpenstackUtil.createOutputIntFlow(sfBridgeId,(short)0,key.toString(),sfmatch,port);

            //create br-sfc flow
            Long sfcportIn = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    sfpSfEntry.getNodeIp(), SFC_2_INT);
            Long sfcportOut = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    sfpSfEntry.getNodeIp(), TUNNEL_POINT);
            String inPortConnId2 = sfSfcBridgeId + ":" +  sfcportIn;
            Match  sfcmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId2);

            StringBuffer key1 = new StringBuffer();
            key1.append(SFC_2_INT).append(TUNNEL_POINT).append(nsp).append(254);
            storeFlowKey(key1,nsp,sfSfcBridgeId);
            SfcOpenstackUtil.createOutputSfcFlow(sfSfcBridgeId,(short)0, key.toString(), sfcmatch, sfcportOut);


            //first step2, create br-sfc flow
            Long sfcportIn2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    destination.getNodeIp(), SFC_2_INT);
            Long sfcportOut2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    destination.getNodeIp(), TUNNEL_POINT);
            String inPortConnId3 = dSfcBridgeId + ":" +  sfcportOut2;
            Match  sfcmatch2 = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId3);

            StringBuffer key2 = new StringBuffer();
            key2.append(TUNNEL_POINT).append(SFC_2_INT).append(nsp).append(254);
            storeFlowKey(key2,nsp,dSfcBridgeId);
            SfcOpenstackUtil.createOutputSfcFlow(dSfcBridgeId,(short)0, key2.toString(),sfcmatch2, sfcportIn2);

            //create br-int flow
            Long sfcport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    destination.getNodeIp(), INT_2_SFC);
            String inPortConnId4 = dBridgeId + ":" +  sfcport;
            Match  dmatch = SfcOpenstackUtil.createNshMatch(nsp,(short)254, inPortConnId4);
            Long dport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    destination.getNodeIp(),
                    destination.getPortName());

            LOG.info("IN openstack listener ADD step42 is: {} <> {}" , dBridgeId,dport);
            StringBuffer key3 = new StringBuffer();
            key3.append(INT_2_SFC).append(destination.getPortName()).append(nsp).append(254);
            storeFlowKey(key3,nsp,dBridgeId);
            SfcOpenstackUtil.createToDestinationIntFlow(dBridgeId,(short)0,key3.toString(),dmatch,dport);
        }
    }

    private void storeFlowKey(StringBuffer key,Long nsp, String bridgeId){
        key.append("|").append(nsp).append("|").append(bridgeId);
        flowList.add(key.toString());
    }

}
