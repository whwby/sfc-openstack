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
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
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


    public static Match createMatch(String srcIp, String dstIp) {
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
        SfcOpenflowUtils.addMatchVlan(matchBuilder,20);
        return matchBuilder.build();

    }

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

    public OvsdbBridgeAugmentation readOvsdbBridge(InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIID) {
        Preconditions.checkNotNull(bridgeIID);
        return SfcDataStoreAPI.readTransactionAPI(bridgeIID, LogicalDatastoreType.OPERATIONAL);
    }

    public String  readOvsdbDatapathIdByNodeId(NodeId nodeId) {
        LOG.info("IN read OVsDb info, which node id is :{}",nodeId);

        Preconditions.checkNotNull(nodeId);
        Topology topology = SfcDataStoreAPI.readTransactionAPI(buildOvsdbTopologyIID(),
                LogicalDatastoreType.OPERATIONAL);
        if (topology.getNode() != null) {
            for (Node node : topology.getNode()) {
                if (node.getNodeId().getValue() == nodeId.toString()) {
                    Long macLong = getLongFromDpid(node.getAugmentation(OvsdbBridgeAugmentation.class)
                            .getDatapathId().getValue());
                    return "openflow:" + String.valueOf(macLong);
                }
            }
        }
        else {
            LOG.warn("In openstack util, read OvsdbNode, Topology operational error!");
        }
        return null;
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
