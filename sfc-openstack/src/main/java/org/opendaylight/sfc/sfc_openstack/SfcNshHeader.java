/*
 * Copyright (c) 2015 who Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.sfc.sfc_openstack;

import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by One_Homeway on 2016/12/13.
 */
public class SfcNshHeader {

    private static final Logger LOG = LoggerFactory.getLogger(SfcNshHeader.class);

    private Ipv4Address vxlanIpDst = null;
    private PortNumber vxlanUdpPort = null;
    private String EncapDst = null;
    private String EncapSrc = null;
    private Long nshNsp = null;
    private Short nshStartNsi = null;
    private Short nshEndNsi = null;
    private Long nshMetaC1 = null;
    private Long nshMetaC2 = null;
    private Long nshMetaC3 = null;
    private Long nshMetaC4 = null;
    private SffName sffName = null;

    public SfcNshHeader() {
    }

    public SfcNshHeader setEncapSrc(String srcMac) {
        this.EncapSrc = srcMac;
        return this;
    }
    public String getEncapSrc() {return this.EncapSrc;}

    public SfcNshHeader setEncapDst(String dstMac) {
        this.EncapDst = dstMac;
        return this;
    }
    public String getEncapDst() {return this.EncapDst;}

    public Ipv4Address getVxlanIpDst() {
        return vxlanIpDst;
    }

    public SfcNshHeader setVxlanIpDst(Ipv4Address vxlanIpDst) {
        this.vxlanIpDst = vxlanIpDst;
        return this;
    }

    public PortNumber getVxlanUdpPort() {
        return vxlanUdpPort;
    }

    public SfcNshHeader setVxlanUdpPort(PortNumber vxlanUdpPort) {
        this.vxlanUdpPort = vxlanUdpPort;
        return this;
    }

    public Long getNshNsp() {
        return nshNsp;
    }

    public SfcNshHeader setNshNsp(Long nshNsp) {
        this.nshNsp = nshNsp;
        return this;
    }

    public Short getNshStartNsi() {
        return nshStartNsi;
    }

    public SfcNshHeader setNshStartNsi(Short nshStartNsi) {
        this.nshStartNsi = nshStartNsi;
        return this;
    }

    public Short getNshEndNsi() {
        return nshEndNsi;
    }

    public SfcNshHeader setNshEndNsi(Short nshEndNsi) {
        this.nshEndNsi = nshEndNsi;
        return this;
    }

    public Long getNshMetaC1() {
        return this.nshMetaC1;
    }

    public SfcNshHeader setNshMetaC1(Long nshMetaC1) {
        this.nshMetaC1 = nshMetaC1;
        return this;
    }

    public Long getNshMetaC2() {
        return this.nshMetaC2;
    }

    public SfcNshHeader getNshMetaC2(Long nshMetaC2) {
        this.nshMetaC2 = nshMetaC2;
        return this;
    }

    public SfcNshHeader setNshMetaC2(Long nshMetaC2) {
        this.nshMetaC2 = nshMetaC2;
        return this;
    }

    public Long getNshMetaC3() {
        return nshMetaC3;
    }

    public SfcNshHeader setNshMetaC3(Long nshMetaC3) {
        this.nshMetaC3 = nshMetaC3;
        return this;
    }

    public Long getNshMetaC4() {
        return nshMetaC4;
    }

    public SfcNshHeader setNshMetaC4(Long nshMetaC4) {
        this.nshMetaC4 = nshMetaC4;
        return this;
    }

    public SffName getSffName() {
        return sffName;
    }

    public SfcNshHeader setSffName(SffName sffName) {
        this.sffName = sffName;
        return this;
    }

    public  SfcNshHeader getSfcNshHeader() {
        Long nsp = new Long(66);
        Long meta = new Long(321);
        Short nsi = 255;
        SfcNshHeader sfcNshHeader = new SfcNshHeader()
                .setNshNsp(nsp)
                .setEncapSrc("11:11:11:22:22:22")
                .setEncapDst("22:22:22:33:33:33")
                .setNshStartNsi(nsi)
                .setNshMetaC1(meta)
                .setNshMetaC2(meta)
                .setNshMetaC3(meta)
                .setNshMetaC4(meta);
        return sfcNshHeader;

    }

}