/*
 * Copyright (c) 2015 who Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.sfc.sfc_openstack;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.sfc.provider.api.SfcDataStoreAPI;
import org.opendaylight.sfc.util.openflow.SfcOpenflowUtils;
import org.opendaylight.ovsdb.southbound.SouthboundConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by One_Homeway on 2016/12/13.
 */
public class SfcOpenstackUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SfcOpenstackUtil.class);
    private static final short testTableId  = 1;
    private static final int FLOW_PRIORITY_CLASSIFIER = 1000;
    private static final short NSH_MDTYPE_ONE = 0x1;
    private static final short NSH_NP_ETH = 0x3;

    private static final String OVSDB_BRIDGE_PREFIX = "/bridge/";



    public static boolean createTestFlow(String nodeName,Long outPort) {
        int order = 0;
        Match match = createMatch("11.11.11.0/24", "11.11.11.0/24");
        //Action popVlan = SfcOpenflowUtils.createActionPopVlan(order++);
        Action stripVlan = SfcOpenflowUtils.createActionStripVlan(order++);
        Action out = SfcOpenflowUtils.createActionOutPort(outPort.intValue(),order++);
        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId("1"))
                .setTableId((short)3)
                .setKey(new FlowKey(new FlowId("1")))
                .setPriority(100)
                .setMatch(match)
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils.
                        createActionsInstructionBuilder(stripVlan,out))
                .build());
        LOG.info("IN openstack util test flow is: {}" + nodeName );
        return SfcOpenflowUtils.writeFlowToDataStore(nodeName, flowb);
    }

    public static boolean createFirstFlowInSameNode(String bridgeId, short tableId, Match match,
                                               SfcNshHeader sfcNshHeader,Long outPort) {
        int order = 0;
        String flowKey="FirstFlowInSameNode";
        //flow action site:br-int
        if((bridgeId == null) || (match == null) ||(sfcNshHeader == null)) {
            return false;
        }
        String srcMac = sfcNshHeader.getEncapSrc();
        String dstMac = sfcNshHeader.getEncapDst();
        Action popVlan = SfcOpenflowUtils.createActionPopVlan(order++);
        Action pushNsh = SfcOpenflowUtils.createActionNxPushNsh(order++);
        Action loadNshMdtype = SfcOpenflowUtils.createActionNxLoadNshMdtype(NSH_MDTYPE_ONE, order++);
        Action loadNshNp = SfcOpenflowUtils.createActionNxLoadNshNp(NSH_NP_ETH, order++);

        Action setNsp = SfcOpenflowUtils.createActionNxSetNsp(sfcNshHeader.getNshNsp(), order++);
        Action setNsi = SfcOpenflowUtils.createActionNxSetNsi(sfcNshHeader.getNshStartNsi(), order++);

        Action setC1 = SfcOpenflowUtils.createActionNxSetNshc1(sfcNshHeader.getNshMetaC1(), order++);
        Action setC2 = SfcOpenflowUtils.createActionNxSetNshc2(sfcNshHeader.getNshMetaC2(), order++);
        Action setC3 = SfcOpenflowUtils.createActionNxSetNshc3(sfcNshHeader.getNshMetaC3(), order++);
        Action setC4 = SfcOpenflowUtils.createActionNxSetNshc4(sfcNshHeader.getNshMetaC4(), order++);

        Action setEncapSrc = SfcOpenflowUtils.createActionNxLoadEncapEthSrc(srcMac, order++);
        Action setEncapDst = SfcOpenflowUtils.createActionNxLoadEncapEthDst(dstMac,order++);

        Action out = null;
        if (outPort == null) {
            out = SfcOpenflowUtils.createActionOutPort(OutputPortValues.INPORT.toString(), order++);
        } else {
            out = SfcOpenflowUtils.createActionOutPort(outPort.intValue(), order++);
        }

        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(tableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER))
                .setMatch(match)
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(popVlan, pushNsh, loadNshMdtype, loadNshNp,
                                setNsp, setNsi, setC1, setC2, setC3, setC4, setEncapSrc,setEncapDst,out))
                        .build());
        LOG.info("IN openstack util createFirstFlowInSameNode is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(bridgeId, flowb);
    }

    public static boolean createFlowFromVNF(String bridgeId,short tableId, Match match, Long outport) {

        //let suppose only a sf, so the outport is dst
        int order = 0;
        String flowKey="createFlowFromVNF";
        Action popNsh = SfcOpenflowUtils.createActionNxPopNsh(order++);
        Action out = SfcOpenflowUtils.createActionOutPort(outport.intValue(),order++);

        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(tableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER))
                .setMatch(match)
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(popNsh,out))
                        .build());
        LOG.info("IN openstack util createFlowFromVNF is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(bridgeId, flowb);

    }

    public static boolean createFirstFlowInDifferentNode(String bridgeId, short tableId, Match match,
                                                    SfcNshHeader sfcNshHeader,Long outPort) {
        int order = 0;
        String flowKey="FirstFlowInDifferentNode";
        if((bridgeId == null) || (match == null) ||(sfcNshHeader == null)) {
            return false;
        }
        Action popVlan = SfcOpenflowUtils.createActionPopVlan(order++);
        Action pushNsh = SfcOpenflowUtils.createActionNxPushNsh(order++);
        Action loadNshMdtype = SfcOpenflowUtils.createActionNxLoadNshMdtype(NSH_MDTYPE_ONE, order++);
        Action loadNshNp = SfcOpenflowUtils.createActionNxLoadNshNp(NSH_NP_ETH, order++);

        Action setNsp = SfcOpenflowUtils.createActionNxSetNsp(sfcNshHeader.getNshNsp(), order++);
        Action setNsi = SfcOpenflowUtils.createActionNxSetNsi(sfcNshHeader.getNshStartNsi(), order++);

        Action setC1 = SfcOpenflowUtils.createActionNxSetNshc1(sfcNshHeader.getNshMetaC1(), order++);
        Action setC2 = SfcOpenflowUtils.createActionNxSetNshc2(sfcNshHeader.getNshMetaC2(), order++);
        Action setC3 = SfcOpenflowUtils.createActionNxSetNshc3(sfcNshHeader.getNshMetaC3(), order++);
        Action setC4 = SfcOpenflowUtils.createActionNxSetNshc4(sfcNshHeader.getNshMetaC4(), order++);

        Action out = null;
        if (outPort == null) {
            out = SfcOpenflowUtils.createActionOutPort(OutputPortValues.INPORT.toString(), order++);
        } else {
            out = SfcOpenflowUtils.createActionOutPort(outPort.intValue(), order++);
        }

        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(tableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER))
                .setMatch(match)
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(popVlan, pushNsh, loadNshMdtype, loadNshNp,
                                setNsp, setNsi, setC1, setC2, setC3, setC4,out))
                        .build());
        LOG.info("IN openstack util createFirstFlowInDifferentNode is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(bridgeId, flowb);
    }

    public static boolean createFlowToVNF(String bridgeId,short tableId, Match match,
                                          SfcNshHeader sfcNshHeader, Long outport) {

        int order = 0;
        String flowKey="createFlowToVNF";
        String srcMac = sfcNshHeader.getEncapSrc();
        String dstMac = sfcNshHeader.getEncapDst();
        Action out = SfcOpenflowUtils.createActionOutPort(outport.intValue(),order++);
        Action setEncapSrc = SfcOpenflowUtils.createActionNxLoadEncapEthSrc(srcMac, order++);
        Action setEncapDst = SfcOpenflowUtils.createActionNxLoadEncapEthDst(dstMac,order++);

        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(tableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER))
                .setMatch(match)
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(setEncapSrc,setEncapDst,out))
                        .build());
        LOG.info("IN openstack util createFlowToVNF is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(bridgeId, flowb);

    }

    public static boolean createFlowToNormal(String bridgeId,short tableId, Match match, Long outport) {

        int order = 0;
        String flowKey="createFlowToNormal";
        Action out = SfcOpenflowUtils.createActionOutPort(outport.intValue(),order++);

        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(tableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER))
                .setMatch(match)
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(out))
                        .build());
        LOG.info("IN openstack util createFlowToNormal is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(bridgeId, flowb);

    }

    public static boolean createOtherIntFlows(String bridgeId, short tableId) {
        int order = 0;
        String flowKey = "createOtherIntFlows";
        Action normalAction = SfcOpenflowUtils.createActionNormal(order++);
        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(tableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER-10))
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(normalAction))
                        .build());
        LOG.info("IN openstack util createOtherIntFlows is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(bridgeId, flowb);
    }

    public static boolean createOtherSfcFlows(String bridgeId, short tableId) {
        int order = 0;
        String flowKey = "createOtherSfcFlows";
        Action dropAction = SfcOpenflowUtils.createActionDropPacket(order++);
        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(tableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER-10))
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(dropAction))
                        .build());
        LOG.info("IN openstack util createOtherSfcFlows is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(bridgeId, flowb);
    }

    public static boolean createSfcFlows(String bridgeId, short tableId, Match match, Long outport) {
        int order = 0;
        String flowKey = "createSfcFlows";
        Action out = SfcOpenflowUtils.createActionOutPort(outport.intValue(), order++);
        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(tableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER))
                .setMatch(match)
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(out))
                        .build());
        LOG.info("IN openstack util createSfcFlows is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(bridgeId, flowb);
    }


    public static boolean createClassifierInFlow(String nodeName, String flowKey, Match match, SfcNshHeader sfcNshHeader,
                                                  Long outPort) {
        int order = 0;

        if ((nodeName == null) || (sfcNshHeader == null)) {
            return false;
        }
        if (flowKey == null) {
            flowKey = "testFlowKey";
        }
        if (match == null) {
            match = createMatch("192.168.0.1/32", "192.168.0.2/32");
        }

        String srcMac = sfcNshHeader.getEncapSrc();
        String dstMac = sfcNshHeader.getEncapDst();
        Action popVlan = SfcOpenflowUtils.createActionPopVlan(order++);
        Action pushNsh = SfcOpenflowUtils.createActionNxPushNsh(order++);
        Action loadNshMdtype = SfcOpenflowUtils.createActionNxLoadNshMdtype(NSH_MDTYPE_ONE, order++);
        Action loadNshNp = SfcOpenflowUtils.createActionNxLoadNshNp(NSH_NP_ETH, order++);

        Action setNsp = SfcOpenflowUtils.createActionNxSetNsp(sfcNshHeader.getNshNsp(), order++);
        Action setNsi = SfcOpenflowUtils.createActionNxSetNsi(sfcNshHeader.getNshStartNsi(), order++);

        Action setC1 = SfcOpenflowUtils.createActionNxSetNshc1(sfcNshHeader.getNshMetaC1(), order++);
        Action setC2 = SfcOpenflowUtils.createActionNxSetNshc2(sfcNshHeader.getNshMetaC2(), order++);
        Action setC3 = SfcOpenflowUtils.createActionNxSetNshc3(sfcNshHeader.getNshMetaC3(), order++);
        Action setC4 = SfcOpenflowUtils.createActionNxSetNshc4(sfcNshHeader.getNshMetaC4(), order++);

        Action setEncapSrc = SfcOpenflowUtils.createActionNxLoadEncapEthSrc(srcMac, order++);
        Action setEncapDst = SfcOpenflowUtils.createActionNxLoadEncapEthDst(dstMac,order++);

        Action out = null;
        if (outPort == null) {
            out = SfcOpenflowUtils.createActionOutPort(OutputPortValues.INPORT.toString(), order++);
        } else {
            out = SfcOpenflowUtils.createActionOutPort(outPort.intValue(), order++);
        }

        FlowBuilder flowb = new FlowBuilder();
        flowb.setId(new FlowId(flowKey))
                .setTableId(testTableId)
                .setKey(new FlowKey(new FlowId(flowKey)))
                .setPriority(Integer.valueOf(FLOW_PRIORITY_CLASSIFIER))
                .setMatch(match)
                .setInstructions(SfcOpenflowUtils.createInstructionsBuilder(SfcOpenflowUtils
                        .createActionsInstructionBuilder(popVlan, pushNsh, loadNshMdtype, loadNshNp,
                                setNsp, setNsi, setC1, setC2, setC3, setC4, setEncapSrc,setEncapDst,out))
                        .build());
        LOG.info("IN openstack util flow is: {}" + flowb );
        return SfcOpenflowUtils.writeFlowToDataStore(nodeName, flowb);
    }

    private static NodeId getManagedByNodeId(OvsdbBridgeAugmentation ovsdbBirdge) {
        String bridgeName = ovsdbBirdge.getBridgeName().getValue();
        InstanceIdentifier<Node> nodeIID = (InstanceIdentifier<Node>) ovsdbBirdge.getManagedBy().getValue();
        KeyedInstanceIdentifier keyedInstanceIdentifier = (KeyedInstanceIdentifier) nodeIID.firstIdentifierOf(Node.class);
        NodeKey nodeKey = (NodeKey) keyedInstanceIdentifier.getKey();
        String  nodeId = nodeKey.getNodeId().getValue();
        nodeId = nodeId.concat(OVSDB_BRIDGE_PREFIX + bridgeName);
        return new NodeId(nodeId);
    }

    public static InstanceIdentifier<OvsdbBridgeAugmentation> buildOvsdbBridgeIID(OvsdbBridgeAugmentation ovsdbBridge) {
        InstanceIdentifier<OvsdbBridgeAugmentation> bridgeEntryIID = InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundConstants.OVSDB_TOPOLOGY_ID))
                .child(Node.class, new NodeKey(getManagedByNodeId(ovsdbBridge)))
                .augmentation(OvsdbBridgeAugmentation.class);
        return bridgeEntryIID;
    }

    public static InstanceIdentifier<Topology> buildOvsdbTopologyIID() {
        InstanceIdentifier<Topology> ovsdbTopologyIID = InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundConstants.OVSDB_TOPOLOGY_ID));
        return ovsdbTopologyIID;
    }

    public static String getBridgeID(String bridgeName, String nodeIp) {
        Topology topology = SfcDataStoreAPI.readTransactionAPI(buildOvsdbTopologyIID(),
                LogicalDatastoreType.OPERATIONAL);
        NodeId bridgeNodeId = getBridgeNodeId(bridgeName,nodeIp);
        String bridgeId = null;
        if (bridgeNodeId != null){
            for (Node node:topology.getNode()) {
                if (node.getNodeId() == bridgeNodeId) {
                    OvsdbBridgeAugmentation ovsdbNodeAug = node.getAugmentation(OvsdbBridgeAugmentation.class);
                    String datapathId = ovsdbNodeAug.getDatapathId().getValue();
                    bridgeId = "openflow:" + String.valueOf(getLongFromDpid(datapathId));
                }
            }
        }
        return bridgeId;
    }

    private static NodeId getBridgeNodeId(String bridgeName, String nodeIp) {
        Topology topology = SfcDataStoreAPI.readTransactionAPI(buildOvsdbTopologyIID(),
                LogicalDatastoreType.OPERATIONAL);
        if (topology.getNode() != null) {
            String nodeId = null;
            for (Node node : topology.getNode()) {
                OvsdbNodeAugmentation ovsdbNodeAug = node.getAugmentation(OvsdbNodeAugmentation.class);
                if (ovsdbNodeAug != null && ovsdbNodeAug.getConnectionInfo() != null) {
                    String remoteIp = ovsdbNodeAug.getConnectionInfo().getRemoteIp().toString();
                    if (remoteIp == nodeIp) {
                        nodeId = node.getNodeId().getValue();
                        break;
                    } else {
                        continue;
                    }
                }
            }
            if (nodeId != null) {
                String bridgeNode = nodeId.concat(OVSDB_BRIDGE_PREFIX + bridgeName);
                return new NodeId(bridgeNode);
            }
        }
        return null;
    }


    public static Match createMatch(String srcIp, String dstIp, int vlanId, int port) {
        MatchBuilder matchBuilder = new MatchBuilder();
        SfcOpenflowUtils.addMatchEtherType(matchBuilder, SfcOpenflowUtils.ETHERTYPE_IPV4);
        if (srcIp != null) {
            String s[] = srcIp.split("/");
            SfcOpenflowUtils.addMatchSrcIpv4(matchBuilder,s[0],Integer.valueOf(s[1]).intValue());
        }
        if(dstIp != null) {
            String d[] = dstIp.split("/");
            SfcOpenflowUtils.addMatchDstIpv4(matchBuilder,d[0],Integer.valueOf(d[1]).intValue());
        }
        if(port != -1) {
            SfcOpenflowUtils.addMatchDstTcpPort(matchBuilder, port);
        }
        if(vlanId != -1){
            SfcOpenflowUtils.addMatchVlan(matchBuilder,vlanId);
        }
        return matchBuilder.build();
    }

    public static Match createMatch(String srcIp, String dstIp) {
        return createMatch(srcIp,dstIp,1,-1);
    }

    public static Match createMatch(IpAddress srcIp, IpAddress dstIp) {
        return createMatch(srcIp.toString(),dstIp.toString());
    }

    public static Match createNshMatch(Long nsp, short nsi, String ConnId) {
        MatchBuilder matchBuilder = new MatchBuilder();
        NodeConnectorId nodeConnId = new NodeConnectorId(ConnId);

        SfcOpenflowUtils.addMatchInPort(matchBuilder,nodeConnId);
        SfcOpenflowUtils.addMatchNshNsp(matchBuilder,nsp);
        SfcOpenflowUtils.addMatchNshNsi(matchBuilder,nsi);
        return matchBuilder.build();
    }


    public static Long getSfcPort(String bridgeName, String nodeIp, String portName) {
        Topology topology = SfcDataStoreAPI.readTransactionAPI(buildOvsdbTopologyIID(),
                LogicalDatastoreType.OPERATIONAL);
        NodeId bridgeNodeId = getBridgeNodeId(bridgeName,nodeIp);
        if (bridgeNodeId != null){
            for (Node node:topology.getNode()) {
                if (node.getNodeId() == bridgeNodeId) {
                    for(TerminationPoint tPoint : node.getTerminationPoint()) {
                        if(tPoint.getTpId().getValue() == portName) {
                            OvsdbTerminationPointAugmentation tPointAug =
                                    tPoint.getAugmentation(OvsdbTerminationPointAugmentation.class);
                            return tPointAug.getOfport();
                        }
                        else{
                            continue;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static SfcNshHeader createSfcNshHeader(Long nsp, short nsi, String dstEncapMac) {
        if(dstEncapMac == null) {
            SfcNshHeader sfcNshHeader = new SfcNshHeader()
                    .setNshNsp(nsp)
                    .setNshStartNsi(nsi)
                    .setNshMetaC1(1L)
                    .setNshMetaC2(2L)
                    .setNshMetaC3(3L)
                    .setNshMetaC4(4L);
            return sfcNshHeader;
        }
        else {
            SfcNshHeader sfcNshHeader = new SfcNshHeader()
                    .setNshNsp(nsp)
                    .setEncapSrc("11:11:22:22:33:33")
                    .setEncapDst(dstEncapMac)
                    .setNshStartNsi(nsi)
                    .setNshMetaC1(1L)
                    .setNshMetaC2(2L)
                    .setNshMetaC3(3L)
                    .setNshMetaC4(4L);
            return sfcNshHeader;
        }
    }

    private static Long getLongFromDpid(String dpid) {
        String HEX = "0x";
        String[] addressInBytes = dpid.split(":");
        Long address = (Long.decode(HEX + addressInBytes[2]) << 40) | (Long.decode(HEX + addressInBytes[3]) << 32)
                | (Long.decode(HEX + addressInBytes[4]) << 24) | (Long.decode(HEX + addressInBytes[5]) << 16)
                | (Long.decode(HEX + addressInBytes[6]) << 8) | (Long.decode(HEX + addressInBytes[7]));
        return address;
    }
}
