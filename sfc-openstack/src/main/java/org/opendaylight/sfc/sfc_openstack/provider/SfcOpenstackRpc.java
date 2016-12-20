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

package org.opendaylight.sfc.sfc_openstack.provider;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.common.util.Rpcs;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.*;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.base.SfpEntries;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.entries.SfcEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.entries.SfcEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.rev161218.sfc.entries.SfcEntryKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.*;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.sff.base.SfList;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.sff.list.SffEntry;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.sff.list.SffEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.openstack.test.rev151212.sff.list.SffEntryKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


//import static org.opendaylight.sfc.provider.SfcProviderDebug.printTraceStart;



public class SfcOpenstackRpc implements SfcOpenstackTestService, SfcOpenstackService {

    private static final Logger LOG = LoggerFactory.getLogger(SfcOpenstackRpc.class);
    private static DataBroker dataBroker = null;

    public SfcOpenstackRpc(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public static void setDataBroker(DataBroker r) {
        dataBroker = r;
    }

    public static void setDataProviderAux(DataBroker r) {
        dataBroker = r;
    }


    @Override
    public Future<RpcResult<Void>> deleteSff(DeleteSffInput input) {
        return null;
    }

    @Override
    public Future<RpcResult<Void>> deleteSfc(DeleteSfcInput input){ return null; }


    @Override
    public Future<RpcResult<Void>> putSfc(PutSfcInput input) {
        LOG.info("\n***************openstack sfc put input" + input);
        if(dataBroker == null) {
            return Futures.immediateFuture(
                    RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, "no data provider.").build());
        }
        SfcEntryBuilder sfcEntryBuilder = new SfcEntryBuilder();
        SfpEntries sfpEntries = input.getSfpEntries();
        SfcEntryKey sfcEntryKey = new SfcEntryKey(input.getName());
        SfcEntry sfc = sfcEntryBuilder.setName(input.getName())
                .setDescription(input.getDescription())
                .setKey(sfcEntryKey)
                .setSfpEntries(sfpEntries)
                .build();
        InstanceIdentifier<SfcEntry> sfcIID =
                InstanceIdentifier.builder(SfcEntries.class).child(SfcEntry.class, sfc.getKey()).build();
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, sfcIID,sfc, true);
        return Futures.transform(writeTx.submit(), new Function<Void, RpcResult<Void>>() {
            @Override
            public RpcResult<Void> apply(Void input) { return RpcResultBuilder.<Void>success().build();}
        });
    }



    @Override
    public Future<RpcResult<Void>> putSff(PutSffInput input) {
//        printTraceStart(LOG);
        LOG.info("\n*******openstack sfc put Input: " + input);
        if (dataBroker == null) {
            return Futures.immediateFuture(
                    RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, "no data provider.").build());

        }
        SffEntryBuilder sffEntryBuilder = new SffEntryBuilder();
        SfList sfList = input.getSfList();
        SffEntryKey sffEntryKey = new SffEntryKey(input.getName());
        SffEntry sff = sffEntryBuilder.setName(input.getName())
                .setBridgeName(input.getBridgeName())
                .setNode(input.getNode())
                .setKey(sffEntryKey)
                .setSfList(sfList)
                .build();

        InstanceIdentifier<SffEntry> sffEntryIID =
                InstanceIdentifier.builder(SffList.class).child(SffEntry.class, sff.getKey()).build();

        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, sffEntryIID, sff, true);
//        printTraceStart(LOG);
        return Futures.transform(writeTx.submit(), new Function<Void, RpcResult<Void>>() {
            @Override
            public RpcResult<Void> apply(Void input) {
                return RpcResultBuilder.<Void>success().build();
            }
        });

    }

    @Override
    public Future<RpcResult<ReadSffOutput>> readSff(ReadSffInput input) {
//        printTraceStart(LOG);
        LOG.info("openstack sfc read input:" + input);
        LOG.warn("\n openstack rpc read input:" + input);


        if (dataBroker != null) {
            SffEntryKey sffEntryKey = new SffEntryKey(input.getName());
            InstanceIdentifier<SffEntry> sffIID;
            sffIID = InstanceIdentifier.builder(SffList.class).child(SffEntry.class, sffEntryKey).build();
            ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction();
            Optional<SffEntry> dataObject = null;
            try {
                dataObject = readTx.read(LogicalDatastoreType.CONFIGURATION, sffIID).get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.debug("openstack failed to read sff:{}", e.getMessage());
            }
            if(dataObject != null && dataObject.isPresent()) {
                SffEntry sffEntry = dataObject.get();
                LOG.warn("openstack read sff success: {}", sffEntry.getName());
                ReadSffOutput readSffOutput = null;
                ReadSffOutputBuilder outputBuilder = new ReadSffOutputBuilder();
                outputBuilder.setName(sffEntry.getName())
                        .setBridgeName(sffEntry.getBridgeName())
                        .setNode(sffEntry.getNode());
                readSffOutput = outputBuilder.build();
//                printTraceStart(LOG);
                return Futures.immediateFuture((Rpcs.<ReadSffOutput>getRpcResult(true,
                        readSffOutput, Collections.<RpcError>emptySet())));
            }
        }
        return null;
    }


}
