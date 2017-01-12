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
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.SfpPoint;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.SfcBridge;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.SfcMatch;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.SfpEntries;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.sfp.entries.sfp.sf.entries.SfpSfEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.entries.SfcEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.sfp.entries.Source;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.sfp.entries.Destination;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.opendaylight.sfc.sfc_openstack.SfcOpenstackUtil;
import org.opendaylight.sfc.sfc_openstack.SfcNshHeader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class SfcOpenstackListener extends SfcOpenstackAbstractListener<SfcEntry> {

    private static final String INT_2_SFC = "int2sfc";
    private static final String SFC_2_INT = "sfc2int";
    private static final String TUNNEL_POINT = "spt6633";
    private static final short NSH_NSI_255 = 255;
    private static final short TABLE_INDEX_0 = 0;
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

            ArrayList<String> bridgeList = new ArrayList<>();
            SfpEntries sfpEntries = sfcEntry.getSfpEntries();
            Source source = sfpEntries.getSource();
            Destination destination = sfpEntries.getDestination();

            Long nsp = sfcEntry.getSfcNsp();
            SfcBridge sfcBridge = sfcEntry.getSfcBridge();
            String intBridgeName = sfcBridge.getConvergeBridgeName();

            String sBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,source.getNodeIp());
            String dBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,destination.getNodeIp());
            List<SfpSfEntry> sfpSfEntryList = sfpEntries.getSfpSfEntries().getSfpSfEntry();
            int listSize = sfpSfEntryList.size();
            sortSfEntryBasedOnIndex(sfpSfEntryList);
            if (listSize < 1) {
                LOG.warn("Add sfc sf entry must have at least one.");
                return;
            }
            else if (listSize == 1){
                SfpSfEntry sfpSfEntry = sfpSfEntryList.get(0);
                String sfBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,sfpSfEntry.getNodeIp());

                createSource2SF(source,sfpSfEntry,nsp,NSH_NSI_255, sfcEntry.getSfcMatch(),sfcBridge);

                short nsi = (short)(NSH_NSI_255 - listSize);
                createSF2Destination(sfpSfEntry,destination,nsp,nsi,sfcBridge);

                //create default br-int normal flow, br-sfc drop flow;
                createDefaultFlows(bridgeList,sBridgeId,source,nsp);
                createDefaultFlows(bridgeList,dBridgeId,destination,nsp);

                createDefaultFlows(bridgeList,sfBridgeId,sfpSfEntry,nsp);
            }
            else {
                SfpSfEntry firstSfpSfEntry = sfpSfEntryList.get(0);
                SfpSfEntry lastSfpSfEntry = sfpSfEntryList.get(listSize-1);

                createSource2SF(source,firstSfpSfEntry,nsp,NSH_NSI_255,sfcEntry.getSfcMatch(),sfcBridge);
                createDefaultFlows(bridgeList,sBridgeId,source,nsp);

                for (int index=0; index<listSize-1; index++) {
                    SfpSfEntry preSfEntry = sfpSfEntryList.get(index);
                    SfpSfEntry nextSfEntry = sfpSfEntryList.get(index+1);
                    String preBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,preSfEntry.getNodeIp());
                    String nextBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,nextSfEntry.getNodeIp());

                    short nsi = (short)(NSH_NSI_255 - index - 1);
                    createSF2SF(preSfEntry,nextSfEntry,nsp, nsi,sfcBridge);

                    createDefaultFlows(bridgeList,preBridgeId,preSfEntry,nsp);
                    createDefaultFlows(bridgeList,nextBridgeId,nextSfEntry,nsp);
                }

                short lastNsi = (short)(NSH_NSI_255 - listSize);
                createSF2Destination(lastSfpSfEntry,destination,nsp,lastNsi,sfcBridge);
                createDefaultFlows(bridgeList,dBridgeId,destination,nsp);
            }
        }
    }


    @Override
    public void remove(SfcEntry sfcEntry) {
        if (sfcEntry != null) {
            LOG.info("Deleting Sfc entry: {}", sfcEntry.getName());
            //delete flow table;

            Iterator<String> flowListIterator = flowList.iterator();
            while(flowListIterator.hasNext()) {
                String flowKey = flowListIterator.next();
                String s[] = flowKey.split("\\|");
                LOG.info("Deleting Sfc entry info: {}>>>{}>>>{}", s[2],s[1],s[0]);
                // key|nsh|bridgeName|nodeIp
                if (s[1].equals(sfcEntry.getSfcNsp().toString())) {
                    SfcOpenstackUtil.deleteFlow(s[2], (s[0]+ "|" + s[1]));
                    flowListIterator.remove();
                }
            }
        }
    }

    @Override
    protected void update(SfcEntry orignalSfc, SfcEntry updateSfc) {
        if(orignalSfc.getSfcNsp().equals(updateSfc.getSfcNsp())) {
            LOG.info("Updating for Sfc entry: {}, NSP:{}  not changed!",
                    orignalSfc.getName(),orignalSfc.getSfcNsp());
            remove(orignalSfc);
            add(updateSfc);
        }
        else {
            LOG.info("Updating for Sfc entry: {}, NSP changed!", orignalSfc.getName());
            remove(orignalSfc);
            add(updateSfc);
        }

    }


    private void createSource2SF(Source source, SfpSfEntry sfpSfEntry , Long nsp, short nsi,
                                        SfcMatch sfcMatch,SfcBridge sfcBridge) {
        String intBridgeName = sfcBridge.getConvergeBridgeName();
        String sfcBridgeName = sfcBridge.getTunnelBridgeName();

        String sBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,source.getNodeIp());
        String sfBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,sfpSfEntry.getNodeIp());
        String sSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,source.getNodeIp());
        String sfSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,sfpSfEntry.getNodeIp());
        Boolean sameNode = sBridgeId.equals(sfBridgeId);
        LOG.info("IN openstack listener ADD  createSource2SF  bridges are: {} <> {}" ,sBridgeId,sfBridgeId);
        if (sameNode) {
            Long sPort = SfcOpenstackUtil.getSfcPort(intBridgeName,source.getNodeIp(),source.getPortName());
            String sInPortConnId = sBridgeId + ":" + sPort;
            Match  match = SfcOpenstackUtil.createMatch(sInPortConnId, sfcMatch.getSrcIp(), sfcMatch.getDstIp(),
                    sfcMatch.getProtocolName(),sfcMatch.getPort().getValue());
            Long sfport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(),
                    sfpSfEntry.getPortName());
            SfcNshHeader sfcNshHeader = SfcOpenstackUtil.createSfcNshHeader(nsp, nsi,
                    sfpSfEntry.getSfMac().getValue());

            LOG.info("IN openstack listener ADD createSource2SF step1 is: {} <> {}" , sBridgeId,sfport);
            StringBuffer key = new StringBuffer();
            key.append(source.getPortName()).append("[>>]").append(sfpSfEntry.getPortName()).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createClassifierIntFlow(sBridgeId,TABLE_INDEX_0,key.toString(),
                    match,sfcNshHeader,sfport, sameNode);
            storeFlowKey(key,sBridgeId);
        }
        else {
            //source.br-int->>source.br-sfc-->sfpsfentry.br-sfc-->sfpsfentry.br-int
            //source.br-int
            Long sPort = SfcOpenstackUtil.getSfcPort(intBridgeName,source.getNodeIp(),source.getPortName());
            String sInPortConnId = sBridgeId + ":" + sPort;
            Match  match = SfcOpenstackUtil.createMatch(sInPortConnId,sfcMatch.getSrcIp(),sfcMatch.getDstIp(),
                    sfcMatch.getProtocolName(),sfcMatch.getPort().getValue());
            Long port = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    source.getNodeIp(),
                    INT_2_SFC);
            SfcNshHeader sfcNshHeader = SfcOpenstackUtil.createSfcNshHeader(nsp, nsi,null);

            LOG.info("IN openstack listener ADD createSource2SF step21 is: {} <> {}" , sBridgeId,port);
            StringBuffer key = new StringBuffer();
            key.append(source.getPortName()).append("[>>]").append(INT_2_SFC).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createClassifierIntFlow(sBridgeId,TABLE_INDEX_0,key.toString(),
                    match,sfcNshHeader,port,sameNode);
            storeFlowKey(key,sBridgeId);

            //source.br-sfc

            String sfTunnelPoint = TUNNEL_POINT + "-" + sfpSfEntry.getNodeIp().split("\\.")[3];
            Long sfcportIn = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    source.getNodeIp(), SFC_2_INT);
            Long sfcportOut = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    source.getNodeIp(), sfTunnelPoint);

            String inPortConnId = sSfcBridgeId + ":" +  sfcportIn;
            Match  sfcmatch = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId);

            StringBuffer key1 = new StringBuffer();
            key1.append(SFC_2_INT).append("[>>]").append(sfTunnelPoint).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createOutputSfcFlow(sSfcBridgeId,TABLE_INDEX_0, key1.toString(),sfcmatch, sfcportOut);
            storeFlowKey(key1,sSfcBridgeId);

            //sfpsfentry.br-sfc
            String sTunnelPoint = TUNNEL_POINT + "-" + source.getNodeIp().split("\\.")[3];
            Long sfcportIn2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    sfpSfEntry.getNodeIp(), SFC_2_INT);
            Long sfcportOut2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    sfpSfEntry.getNodeIp(), sTunnelPoint);

            String inPortConnId2 = sfSfcBridgeId + ":" +  sfcportOut2;
            Match  sfcmatch2 = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId2);

            StringBuffer key2 = new StringBuffer();
            key2.append(sTunnelPoint).append("[>>]").append(SFC_2_INT).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createOutputSfcFlow(sfSfcBridgeId,TABLE_INDEX_0, key2.toString(),sfcmatch2, sfcportIn2);
            storeFlowKey(key2,sfSfcBridgeId);

            //sfpsfentry.br-int
            Long sfportIn = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(), INT_2_SFC);
            String inPortConnId3 = sfBridgeId + ":" +  sfportIn;
            Match  sfmatch = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId3);
            Long sfport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(), sfpSfEntry.getPortName());

            SfcNshHeader sfcNshHeader2 = SfcOpenstackUtil.createSfcNshHeader(nsp,
                    (short)255, sfpSfEntry.getSfMac().getValue());

            LOG.info("IN openstack listener ADD createSource2SF step22 is: {} <> {}" ,sfBridgeId,sfport);
            StringBuffer key3 = new StringBuffer();
            key3.append(INT_2_SFC).append("[>>]").append(sfpSfEntry.getPortName()).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createToVNFIntFlow(sfBridgeId,TABLE_INDEX_0,key3.toString(),sfmatch,sfcNshHeader2, sfport);
            storeFlowKey(key3,sfBridgeId);
        }
    }

    private void createSF2SF(SfpSfEntry preSfEntry,SfpSfEntry nextSfEntry, Long nsp, short nsi, SfcBridge sfcBridge) {

        String intBridgeName = sfcBridge.getConvergeBridgeName();
        String sfcBridgeName = sfcBridge.getTunnelBridgeName();

        String preBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,preSfEntry.getNodeIp());
        String nextBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,nextSfEntry.getNodeIp());
        String preSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,preSfEntry.getNodeIp());
        String nextSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,nextSfEntry.getNodeIp());
        Boolean sameNode =  preBridgeId.equals(nextBridgeId);
        LOG.info("IN openstack listener ADD  createSF2SF  bridges are: {} <> {}" ,preBridgeId,nextBridgeId);
        if(sameNode){
            Long prePort = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    preSfEntry.getNodeIp(), preSfEntry.getPortName());
            Long nextPort = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    nextSfEntry.getNodeIp(),
                    nextSfEntry.getPortName());
            String inPortConnId = preBridgeId + ":" +  prePort;
            Match  match = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId);
            SfcNshHeader sfcNshHeader = SfcOpenstackUtil.createSfcNshHeader(nsp,nsi,
                    nextSfEntry.getSfMac().getValue());

            LOG.info("IN openstack listener ADD createSF2SF  step1 is: {} <> {}" , preBridgeId,nextPort);
            StringBuffer key = new StringBuffer();
            key.append(preSfEntry.getPortName()).append("[>>]").append(nextSfEntry.getPortName()).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createToVNFIntFlow(preBridgeId,TABLE_INDEX_0,key.toString(),match,sfcNshHeader,nextPort);
            storeFlowKey(key,preBridgeId);
        }
        else {
            //preNode.br-int-->preNode.br-sfc-->nextNode.br-sfc-->nextNode.br-int
            //preNode.br-int
            Long prePort = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    preSfEntry.getNodeIp(), preSfEntry.getPortName());
            Long sfPort = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    nextSfEntry.getNodeIp(),
                    INT_2_SFC);
            String inPortConnId = preBridgeId + ":" +  prePort;
            Match  match = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId);
            SfcNshHeader sfcNshHeader = SfcOpenstackUtil.createSfcNshHeader(nsp,nsi, null);

            LOG.info("IN openstack listener ADD createSF2SF step21 is: {} <> {}" , preBridgeId,sfPort);
            StringBuffer key = new StringBuffer();
            key.append(preSfEntry.getPortName()).append("[>>]").append(INT_2_SFC).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createOutputIntFlow(preBridgeId,TABLE_INDEX_0,key.toString(),match,sfPort);
            storeFlowKey(key,preBridgeId);

            //preNode.br-sfc
            String nextTunnelPoint = TUNNEL_POINT + "-" + nextSfEntry.getNodeIp().split("\\.")[3];
            Long sfcportIn = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    preSfEntry.getNodeIp(), SFC_2_INT);
            Long sfcportOut = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    preSfEntry.getNodeIp(), nextTunnelPoint);

            String inPortConnId2 = preSfcBridgeId + ":" +  sfcportIn;
            Match  sfcmatch = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId2);

            StringBuffer key1 = new StringBuffer();
            key1.append(SFC_2_INT).append("[>>]").append(nextTunnelPoint).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createOutputSfcFlow(preSfcBridgeId,TABLE_INDEX_0, key1.toString(),sfcmatch, sfcportOut);
            storeFlowKey(key1,preSfcBridgeId);

            //nextNode.br-sfc
            String preTunnelPoint = TUNNEL_POINT + "-" + preSfEntry.getNodeIp().split("\\.")[3];
            Long sfcportIn2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    nextSfEntry.getNodeIp(), SFC_2_INT);
            Long sfcportOut2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    nextSfEntry.getNodeIp(), preTunnelPoint);

            String inPortConnId3 = nextSfcBridgeId + ":" +  sfcportOut2;
            Match  sfcmatch2 = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId3);

            StringBuffer key2 = new StringBuffer();
            key2.append(preTunnelPoint).append("[>>]").append(SFC_2_INT).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createOutputSfcFlow(nextSfcBridgeId,TABLE_INDEX_0, key2.toString(),sfcmatch2, sfcportIn2);
            storeFlowKey(key2,nextSfcBridgeId);

            //nextNode.br-int
            Long sfPort2 = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    nextSfEntry.getNodeIp(), INT_2_SFC);
            Long nextPort = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    nextSfEntry.getNodeIp(), nextSfEntry.getPortName());

            String inPortConnId4 = nextBridgeId + ":" +  sfPort2;
            Match  match2 = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId4);
            SfcNshHeader sfcNshHeader2 = SfcOpenstackUtil.createSfcNshHeader(nsp, nsi,
                    nextSfEntry.getSfMac().getValue());

            LOG.info("IN openstack listener ADD createSF2SF step22 is: {} <> {}" ,nextBridgeId,nextPort);
            StringBuffer key3 = new StringBuffer();
            key3.append(INT_2_SFC).append("[>>]").append(nextSfEntry.getPortName()).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createToVNFIntFlow(nextBridgeId,TABLE_INDEX_0,key3.toString(),match2,sfcNshHeader2, nextPort);
            storeFlowKey(key3,nextBridgeId);
        }
    }

    private void createSF2Destination(SfpSfEntry sfpSfEntry, Destination destination, Long nsp, short nsi,
                                      SfcBridge sfcBridge) {
        String intBridgeName = sfcBridge.getConvergeBridgeName();
        String sfcBridgeName = sfcBridge.getTunnelBridgeName();

        String sfBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,sfpSfEntry.getNodeIp());
        String dBridgeId = SfcOpenstackUtil.getBridgeID(intBridgeName,destination.getNodeIp());
        String sfSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,sfpSfEntry.getNodeIp());
        String dSfcBridgeId = SfcOpenstackUtil.getBridgeID(sfcBridgeName,destination.getNodeIp());
        Boolean sameNode = sfBridgeId.equals(dBridgeId);
        LOG.info("IN openstack listener ADD  createSF2Destination  bridges are: {} <> {}" ,sfBridgeId,dBridgeId);
        if (sameNode) {
            Long sfport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(), sfpSfEntry.getPortName());
            String inPortConnId = dBridgeId + ":" +  sfport;
            Match  match = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId);

            Long dport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    destination.getNodeIp(),
                    destination.getPortName());
            LOG.info("IN openstack listener ADD createSF2Destination step1 is: {} <> {}" , dBridgeId,dport);
            StringBuffer key = new StringBuffer();
            key.append(sfpSfEntry.getPortName()).append("[>>]").append(destination.getPortName()).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createToDestinationIntFlow(dBridgeId,TABLE_INDEX_0,key.toString(),match,dport);
            storeFlowKey(key,dBridgeId);
        }
        else {
            //sfpsfentry.br-int-->sfpsfentry.br-sfc-->destination.br-sfc-->destination.br-int
            //sfpsfentry.br-int
            Long sfport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(), sfpSfEntry.getPortName());
            String inPortConnId = sfBridgeId + ":" +  sfport;
            Match  sfmatch = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId);
            Long port = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    sfpSfEntry.getNodeIp(),
                    INT_2_SFC);

            LOG.info("IN openstack listener ADD createSF2Destination step21 is: {} <> {}" ,sfBridgeId,port);
            StringBuffer key = new StringBuffer();
            key.append(sfpSfEntry.getPortName()).append("[>>]").append(INT_2_SFC).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createOutputIntFlow(sfBridgeId,TABLE_INDEX_0,key.toString(),sfmatch,port);
            storeFlowKey(key,sfBridgeId);

            //sfpsfentry.br-sfc
            String sfTunnelPoint = TUNNEL_POINT + "-" + destination.getNodeIp().split("\\.")[3];
            Long sfcportIn = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    sfpSfEntry.getNodeIp(), SFC_2_INT);
            Long sfcportOut = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    sfpSfEntry.getNodeIp(), sfTunnelPoint);
            String inPortConnId2 = sfSfcBridgeId + ":" +  sfcportIn;
            Match  sfcmatch = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId2);

            StringBuffer key1 = new StringBuffer();
            key1.append(SFC_2_INT).append("[>>]").append(sfTunnelPoint).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createOutputSfcFlow(sfSfcBridgeId,TABLE_INDEX_0, key1.toString(), sfcmatch, sfcportOut);
            storeFlowKey(key1,sfSfcBridgeId);


            //destination.br-sfc
            String dTunnelPoint = TUNNEL_POINT + "-" + sfpSfEntry.getNodeIp().split("\\.")[3];
            Long sfcportIn2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    destination.getNodeIp(), SFC_2_INT);
            Long sfcportOut2 = SfcOpenstackUtil.getSfcPort(sfcBridgeName,
                    destination.getNodeIp(), dTunnelPoint);
            String inPortConnId3 = dSfcBridgeId + ":" +  sfcportOut2;
            Match  sfcmatch2 = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId3);

            StringBuffer key2 = new StringBuffer();
            key2.append(dTunnelPoint).append("[>>]").append(SFC_2_INT).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createOutputSfcFlow(dSfcBridgeId,TABLE_INDEX_0, key2.toString(),sfcmatch2, sfcportIn2);
            storeFlowKey(key2,dSfcBridgeId);

            //destination.br-int
            Long sfcport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    destination.getNodeIp(), INT_2_SFC);
            String inPortConnId4 = dBridgeId + ":" +  sfcport;
            Match  dmatch = SfcOpenstackUtil.createNshMatch(nsp, nsi, inPortConnId4);
            Long dport = SfcOpenstackUtil.getSfcPort(intBridgeName,
                    destination.getNodeIp(),
                    destination.getPortName());

            LOG.info("IN openstack listener ADD createSF2Destination step22 is: {} <> {}" , dBridgeId,dport);
            StringBuffer key3 = new StringBuffer();
            key3.append(INT_2_SFC).append("[>>]").append(destination.getPortName()).append(nsi)
                    .append("|").append(nsp);
            SfcOpenstackUtil.createToDestinationIntFlow(dBridgeId,TABLE_INDEX_0,key3.toString(),dmatch,dport);
            storeFlowKey(key3,dBridgeId);
        }
    }

    private void createDefaultFlows(ArrayList<String> bridgeList, String nodeId, SfpPoint sfpPoint,Long nsp){
        Boolean isInList = false;
        for (String bridgeId : bridgeList) {
            if (nodeId.equals(bridgeId)){
                isInList = true;
                break;
            }
        }
        if (!isInList){
            createDefaultIntFlow(sfpPoint,nsp);
            createDefaultSfcFlow(sfpPoint,nsp);
            bridgeList.add(nodeId);
        }
    }

    private void createDefaultIntFlow(SfpPoint sfpPoint, Long nsp) {
        StringBuffer key = new StringBuffer().append("br-int").append("[>>]").append("normal")
                .append("|").append(nsp);
        String bridgeName = "br-int";
        String bridgeId = SfcOpenstackUtil.getBridgeID(bridgeName,sfpPoint.getNodeIp());
        SfcOpenstackUtil.createNormalIntFlow(bridgeId,TABLE_INDEX_0,key.toString());
        storeFlowKey(key,bridgeId);
    }

    private void createDefaultSfcFlow(SfpPoint sfpPoint,Long nsp) {
        StringBuffer key1 = new StringBuffer().append("br-sfc").append("[>>]").append("drop")
                .append("|").append(nsp);
        String bridgeName = "br-sfc";
        String bridgeId = SfcOpenstackUtil.getBridgeID(bridgeName,sfpPoint.getNodeIp());
        SfcOpenstackUtil.createDropSfcFlow(bridgeId,TABLE_INDEX_0, key1.toString());
        storeFlowKey(key1,bridgeId);
    }

    private void sortSfEntryBasedOnIndex(List<SfpSfEntry> sfpSfEntry){
        //bubble sort
        int size = sfpSfEntry.size();
        for (int i=0;i<size-1;i++) {
            for (int j=0;j<size-i-1;j++) {
                if(sfpSfEntry.get(j).getIndex() > sfpSfEntry.get(j+1).getIndex()){
                    SfpSfEntry tmp = sfpSfEntry.get(j);
                    sfpSfEntry.set(j,sfpSfEntry.get(j+1));
                    sfpSfEntry.set(j+1,tmp);
                }
            }
        }
    }

    private void storeFlowKey(StringBuffer key, String bridgeId){
        key.append("|").append(bridgeId);
        flowList.add(key.toString());
    }

}
