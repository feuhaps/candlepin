/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.gutterball.report;

import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceReason;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.inject.Provider;
import javax.ws.rs.core.MultivaluedMap;

/**
 * ConsumerTrendReport
 */
public class ConsumerTrendReport extends Report<ConsumerTrendReportResult> {

    private ComplianceSnapshotCurator snapshotCurator;
    private StatusReasonMessageGenerator messageGenerator;

    /**
     * @param i18nProvider
     * @param key
     * @param description
     * @param messageGenerator
     */
    @Inject
    public ConsumerTrendReport(Provider<I18n> i18nProvider, ComplianceSnapshotCurator snapshotCurator,
            StatusReasonMessageGenerator messageGenerator) {
        super(i18nProvider, "consumer_trend",
                i18nProvider.get().tr("Lists the status of each consumer over a date range"));
        this.snapshotCurator = snapshotCurator;
        this.messageGenerator = messageGenerator;
    }

    @Override
    protected void initParameters() {
        ReportParameterBuilder builder = new ReportParameterBuilder(i18n);

        addParameter(
            builder.init("consumer_uuid", i18n.tr("Filters the results by the specified consumer UUID."))
                .multiValued()
                .getParameter()
        );

        addParameter(
            builder.init("owner", i18n.tr("The Owner key(s) to filter on."))
                .multiValued()
                .getParameter());

        addParameter(
            builder.init("hours", i18n.tr("The number of hours to filter on (used indepent of date range)."))
                   .mustBeInteger()
                   .mustNotHave("start_date", "end_date")
                   .getParameter());

        addParameter(
            builder.init("start_date", i18n.tr("The start date to filter on (used with {0}).", "end_date"))
                .mustNotHave("hours")
                .mustHave("end_date")
                .mustBeDate(REPORT_DATETIME_FORMAT)
                .getParameter());

        addParameter(
            builder.init("end_date", i18n.tr("The end date to filter on (used with {0})", "start_date"))
                .mustNotHave("hours")
                .mustHave("start_date")
                .mustBeDate(REPORT_DATETIME_FORMAT)
                .getParameter());
    }

    @Override
    protected ConsumerTrendReportResult execute(MultivaluedMap<String, String> queryParams) {

        List<String> consumerIds = queryParams.get("consumer_uuid");
        List<String> ownerFilters = queryParams.get("owner");

        Date startDate = null;
        Date endDate = null;
        // Determine if we should lookup for the last x hours.
        if (queryParams.containsKey("hours")) {
            Calendar cal = Calendar.getInstance();
            endDate = cal.getTime();

            int hours = Integer.parseInt(queryParams.getFirst("hours"));
            cal.add(Calendar.HOUR, hours * -1);
            startDate = cal.getTime();
        }
        else if (queryParams.containsKey("start_date") && queryParams.containsKey("end_date")) {
            startDate = parseDateTime(queryParams.getFirst("start_date"));
            endDate = parseDateTime(queryParams.getFirst("end_date"));
        }


        ConsumerTrendReportResult result = new ConsumerTrendReportResult();
        Set<Compliance> forTimeSpan = snapshotCurator.getComplianceForTimespan(
                startDate, endDate, consumerIds, ownerFilters);
        for (Compliance cs : forTimeSpan) {
            for (ComplianceReason cr : cs.getStatus().getReasons()) {
                messageGenerator.setMessage(cs.getConsumer(), cr);
            }
            result.add(cs.getConsumer().getUuid(), cs);
        }
        return result;
    }

}
