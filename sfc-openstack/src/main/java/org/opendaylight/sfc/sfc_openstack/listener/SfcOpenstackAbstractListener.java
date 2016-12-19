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

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;

public abstract class SfcOpenstackAbstractListener<T extends DataObject>
        implements DataTreeChangeListener<T>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SfcOpenstackAbstractListener.class);

    public SfcOpenstackAbstractListener() {
        LOG.debug("Initializing...");
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<T>> collection) {
        LOG.info("\nopenstack info created SFF:{}", "onDataTreeChange");
        for (final DataTreeModification<T> dataTreeModification : collection) {
            final DataObjectModification<T> dataObjectModification = dataTreeModification.getRootNode();
            switch(dataObjectModification.getModificationType()) {
                case SUBTREE_MODIFIED:
                    update(dataObjectModification.getDataBefore(), dataObjectModification.getDataAfter());
                    break;
                case DELETE:
                    remove(dataObjectModification.getDataBefore());
                    break;
                case WRITE:
                    if (dataObjectModification.getDataBefore() == null) {
                        add(dataObjectModification.getDataAfter());
                    }
                    else {
                        update(dataObjectModification.getDataBefore(), dataObjectModification.getDataAfter());
                    }
                    break;
                default:
                    break;
            }
        }
    }

    protected abstract void add(T newDataObject);

    protected abstract void remove(T newDataObject);

    protected abstract void update(T orginalDataObject, T updatedDataObject);


    //create sff ovsdb object.
    static void addOvsdbAugmentations(String tname) {
        LOG.debug("add OvsdbAugmentations");
    }

}

