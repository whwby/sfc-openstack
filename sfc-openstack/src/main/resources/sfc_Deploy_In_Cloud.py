#! /usr/lib/python2.7
#-*-coding:utf-8 -*-

##environment example
#export OS_USERNAME=admin
#export OS_PASSWORD=admin
#export OS_TENANT_NAME=admin
#export OS_AUTH_URL=http://172.31.10.20:35357/v2.0

import os,sys
import urllib2
import json
from commands import getoutput,getstatusoutput
 
HELP_ARG = "USAGE: python sfc_Deploy_In_Cloud.py [reset] -c controller_ip [-r region-*]"
ERROR_ARG = "In multi-DCs? please specify  the right region with '-r'."
def main():
    #?????
    print "WARNING:this program will restart neutron-openvswtich-agent."
    if len(sys.argv) == 3 and sys.argv[1] == '-c':
        controllerIp = sys.argv[2]
        if len(sys.argv) == 5 and sys.argv[3]== '-r':
            region = sys.argv[4]
            deploySfcInCloud(controllerIp, region)
        else:
            deploySfcInCloud(controllerIp,'')
    elif 'reset' in sys.argv:
        resetSfcInCloud('')
    else:
        print("argument error!")
        print(HELP_ARG)
        return 0 

def deploySfcInCloud(controllerIp, region):
    msgList = ["ip link add int2sfc type veth peer name sfc2int",
           "ip link set dev int2sfc up",
           "ip link set dev sfc2int up",
           "ovs-vsctl add-br br-sfc",
           "ovs-vsctl add-port br-int int2sfc",
           "ovs-vsctl add-port br-sfc sfc2int",
           "ovs-vsctl set-manager tcp:{0}:6640".format(controllerIp),
           "ovs-vsctl set-controller br-int tcp:{0}:6653".format(controllerIp),
           "ovs-vsctl set-controller br-sfc tcp:{0}:6653".format(controllerIp),
           "iptables -I INPUT 2 -p udp --dport 6633 -j ACCEPT",
           "service neutron-openvswitch-agent restart"] 
    computeNode = return_compute_node(region)
    ans = raw_input("NOTE: SFC controller is started?(y/n):")
    if 'n' in ans:
        return 
    for nodei in computeNode:
        for msg in msgList:
            status = -1
            status, output = sendCommand(nodei + " ", msg)
            if status != 0:
                print nodei + ' : ' + msg + " Failed."
                print output
            else:
                print nodei + ' : ' + msg + " OK."
        for nodej in computeNode:
            if nodei != nodej:
                localIp = ipFromNodeName(nodei)
                remoteIp = ipFromNodeName(nodej)
                sptMsg1 = "ovs-vsctl add-port br-sfc spt6633-{0} -- set Interface spt6633-{0} type=vxlan".format(remoteIp.split('.')[3]) 
                #sptMsg1 = "ovs-vsctl add-port br-sfc spt6633 -- set Interface spt6633 type=vxlan"
                sptMsg2 = " options:{dst_port=6633,key=flow,local_ip=" + localIp +",remote_ip="+ remoteIp + "}"
                sptMsg = sptMsg1 + sptMsg2
                status = -1
                status, output = sendCommand(nodei + " ", sptMsg)
                if status != 0:
                    print nodei + ' : ' + sptMsg + " Failed."
                    print output
                else:
                    print nodei + ' : ' + sptMsg + " OK."

def ipFromNodeName(node):
    with open("/etc/hosts") as f:
        for line in f.readlines():
            if node in line:
                return line.split(' ')[0]
 
def  resetSfcInCloud(region):
    msgList = ["ovs-vsctl del-manager",
               "ovs-vsctl del-controller br-int",
               "ovs-vsctl del-controller br-sfc",
               "ovs-vsctl del-br br-sfc",
               "ovs-vsctl del-port br-int int2sfc",
               "ip link delete int2sfc",
               "service neutron-openvswitch-agent restart"]
    computeNode = return_compute_node(region)
    for node in computeNode:
        for msg in msgList:
            stat = -1
            stat, output = sendCommand(node + " ", msg)
            if stat != 0:
                print node + ' : ' + msg + " Failed."
                print output
            else:
                print node + ' : ' + msg + " OK."
        status = -1
        status, output = sendCommand(node + " ", "iptables -L -vn --line-numbers|grep dpt:6633")
        if not status:
            stat = -1
            cmd = "iptables -D INPUT " + output.split(' ')[0]
            stat, output = sendCommand(node + " ", cmd)
            if stat != 0:
                print node + ' : ' + cmd + " Failed."
                print output
            else:
                print node + ' : ' + cmd + " OK."
        
    
def sendCommand(node, com):
    command = "ssh -T -y root@" + node + com
    (status, output) = getstatusoutput(command)
    return status,output

def get_token_and_novaURL(region):
    #??token?cinder???url
    auth_url = getoutput("echo $OS_AUTH_URL")
    t_name = getoutput("echo $OS_TENANT_NAME")
    username = getoutput("echo $OS_USERNAME")
    password = getoutput("echo $OS_PASSWORD")
    if '' in [auth_url, t_name, username, password]:
        print "environment error!"
        sys.exit(0)
    
    region_name = getoutput("echo $OS_REGION_NAME")
    if region:
        reg = region
    else:
        if region_name:
            reg = region_name
        else:
            reg = ''
    
    data_dict = {}
    auth_values = {'auth':
        {'tenantName': t_name,
         'passwordCredentials':
            {'username' : username, 'password' : password}}}
    params = json.dumps(auth_values)
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    try:
        req = urllib2.Request(auth_url+'/tokens', params, headers)
        response = urllib2.urlopen(req)
        data = response.read()
        data_dict = json.loads(data)
    except:
        print 'Request error in get_token_or_cinderURL.'
        sys.exit(0)
    
    #consider multi-regions, get the right region admin_url
    token = data_dict['access']['token']['id']
    tmp = data_dict['access']['serviceCatalog']
    for sid in range(len(tmp)):
        if tmp[sid]['name'] == 'nova':
            if reg:
                e_index = -1
                for ep in range(len(tmp[sid]['endpoints'])):
                    if tmp[sid]['endpoints'][ep]['region'] == reg:
                        e_index = ep
                        break
                if e_index == -1:
                    print ERROR_ARG
                    sys.exit(0)
            else:
                if len(tmp[sid]['endpoints']) != 1:
                    print ERROR_ARG
                    sys.exit(0)
                else:
                    e_index = 0
            novaURL = tmp[sid]['endpoints'][e_index]['adminURL']
    return token, novaURL
    

def return_compute_node(region):
    #return vdisk information, format:dict
    token, nova_url = get_token_and_novaURL(region)
    
    vn_dict = {}
    computeNode = []
    headers = {'X-Auth_Token': token,
               'Content-Type': 'application/json',
               'Accept': 'application/json',
               'User_Agent': 'python-novaclient'}
    url = nova_url + '/os-hosts'
    
    try:
        req = urllib2.Request(url, None, headers)
        response = urllib2.urlopen(req)
        data = response.read()
        vn_dict = json.loads(data)
    except:
        print "Request error in return_compute_node."
        sys.exit(0)
    for host in vn_dict['hosts']:
        if host['service'] == "compute":
            computeNode.append(host['host_name'])
    
    return computeNode

if __name__ == "__main__":
    main()
   
    
