/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.controller;

import java.util.Date;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationResult;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

public class Entitler {
    
    private EntitlementPoolCurator epCurator;
    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;
    private Enforcer enforcer;
    private static Logger log = Logger.getLogger(Entitler.class);
    
    @Inject
    protected Entitler(EntitlementPoolCurator epCurator,
            EntitlementCurator entitlementCurator, ConsumerCurator consumerCurator,
            Enforcer enforcer) {
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
    }

    /**
     * Create an entitlement.
     * 
     * @param entPool
     * @param consumer
     * @return
     */
    //
    // NOTE: after calling this method both entitlement pool and consumer parameters
    //       will most certainly be stale. beware!
    //
    @Transactional
    public Entitlement createEntitlement(Owner owner, Consumer consumer, Product product) {
        
        EntitlementPool ePool = epCurator.lookupByOwnerAndProduct(owner, consumer, product);
        if (ePool == null) {
            throw new RuntimeException("No entitlements for product: " + product.getName());
        }
        
        ValidationResult result = enforcer.validate(consumer, ePool);
        if (result.isSuccessful()) {
        
            Entitlement e = new Entitlement(ePool, consumer, new Date());

            consumer.addEntitlement(e);
            consumer.addConsumedProduct(product);

            if (!result.getFreeEntitlement()) {
                log.debug("Granting free entitlement.");
                ePool.bumpCurrentMembers();
            }
            else {
                // Signal that this entitlement was granted for free:
                e.setIsFree(Boolean.TRUE);
            }

            entitlementCurator.create(e);
            consumerCurator.update(consumer);
            epCurator.merge(ePool);

            enforcer.runPostEntitlementActions(e);

            return e;
        }
        else {
            log.warn("Entitlement not granted: " + result.getErrors().toString());
            return null;
        }
    }
    
}
