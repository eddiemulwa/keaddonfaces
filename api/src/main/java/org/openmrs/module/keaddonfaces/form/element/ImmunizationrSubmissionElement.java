/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.keaddonfaces.form.element;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.module.htmlformentry.*;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.widget.CheckboxWidget;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.openmrs.util.OpenmrsUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Submission element for immunizations
 */
public class ImmunizationrSubmissionElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

    private String id;

    private Concept vaccineConcept;

    private Integer sequenceNumber;

    private String label;

    private CheckboxWidget widget;

    private ErrorWidget errorWidget;

    private Obs existingObsGroup;



    // Question concepts

    // Question concepts
    private static final String IMMUNIZATION_GROUP = "1421AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String IMMUNIZATION_VACCINE = "984AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String IMMUNIZATION_SEQUENCE_NUMBER = "1418AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    //private Concept immunizationGroupConcept = HtmlFormEntryUtil.getConcept(Dictionary.IMMUNIZATION_HISTORY);
    //private Concept vaccineQuestionConcept = HtmlFormEntryUtil.getConcept(Dictionary.IMMUNIZATIONS);
    //private Concept sequenceNumberConcept = HtmlFormEntryUtil.getConcept(Dictionary.IMMUNIZATION_SEQUENCE_NUMBER);

    private Concept immunizationGroupConcept = HtmlFormEntryUtil.getConcept(IMMUNIZATION_GROUP);
    private Concept vaccineQuestionConcept = HtmlFormEntryUtil.getConcept(IMMUNIZATION_VACCINE);
    private Concept sequenceNumberConcept = HtmlFormEntryUtil.getConcept(IMMUNIZATION_SEQUENCE_NUMBER);

    /**
     * Constructs a new immunization submission element
     * @param context the form entry context
     * @param parameters the tag parameters
     */
    public ImmunizationrSubmissionElement(FormEntryContext context, Map<String, String> parameters) {
        String vaccineConceptId = parameters.get("vaccineConceptId");
        if (StringUtils.isEmpty(vaccineConceptId)) {
            throw new RuntimeException("vaccineConceptId attribute required");
        }

        id = parameters.get("id");
        vaccineConcept = HtmlFormEntryUtil.getConcept(vaccineConceptId);
        sequenceNumber = parameters.containsKey("sequenceNumber") ? Integer.parseInt(parameters.get("sequenceNumber")) : null;
        label = parameters.get("label");

        widget = new CheckboxWidget(getLabel(), "true");
        errorWidget = new ErrorWidget();

        context.registerWidget(widget);
        context.registerErrorWidget(widget, errorWidget);

        // Checkbox is checked if we can find a matching obs group
        existingObsGroup = matchExistingObsGroup(context);
        widget.setValue("true");
        widget.setInitialValue(existingObsGroup != null ? Boolean.TRUE : null);
    }

    /**
     * @see org.openmrs.module.htmlformentry.element.HtmlGeneratorElement#generateHtml(org.openmrs.module.htmlformentry.FormEntryContext)
     */
    @Override
    public String generateHtml(FormEntryContext context) {
        boolean viewMode = context.getMode().equals(FormEntryContext.Mode.VIEW);
        StringBuilder sb = new StringBuilder();

        if (id != null) {
            sb.append("<span id=\"" + id + "\">");
            context.registerPropertyAccessorInfo(id + ".value", context.getFieldNameIfRegistered(widget), null, null, null);
            context.registerPropertyAccessorInfo(id + ".error", context.getFieldNameIfRegistered(errorWidget), null, null, null);
        }

        sb.append(widget.generateHtml(context));

        if (!viewMode) {
            sb.append(errorWidget.generateHtml(context));
        }

        if (id != null) {
            sb.append("</span>");
        }

        return sb.toString();
    }

    /**
     * Gets the label to use for this control
     * @return the label
     */
    protected String getLabel() {
        if (label != null) {
            return label;
        }

        String ret = vaccineConcept.getName().getName();
        if (sequenceNumber != null) {
            ret += "-" + sequenceNumber;
        }
        return ret;
    }

    /**
     * @see org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction#validateSubmission(org.openmrs.module.htmlformentry.FormEntryContext, javax.servlet.http.HttpServletRequest)
     */
    @Override
    public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest httpServletRequest) {
        return Collections.emptyList();
    }

    /**
     * @see org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction#handleSubmission(org.openmrs.module.htmlformentry.FormEntrySession, javax.servlet.http.HttpServletRequest)
     */
    @Override
    public void handleSubmission(FormEntrySession session, HttpServletRequest request) {
        boolean checked = widget.getValue(session.getContext(), request) != null;

        if (checked && existingObsGroup == null) {
            try {
                Obs group = new Obs();
                group.setConcept(immunizationGroupConcept);

                session.getSubmissionActions().beginObsGroup(group);
                session.getSubmissionActions().createObs(vaccineQuestionConcept, vaccineConcept, null, null, null);
                session.getSubmissionActions().createObs(sequenceNumberConcept, sequenceNumber, null, null, null);
                session.getSubmissionActions().endObsGroup();
            }
            catch (InvalidActionException e) {
                e.printStackTrace();
            }
        }
        else if (!checked && existingObsGroup != null) {
            session.getSubmissionActions().getObsToVoid().add(existingObsGroup);
        }
    }

    /**
     * Matches an existing obs group
     * @param context the form entry context
     * @return the obs or null
     */
    protected Obs matchExistingObsGroup(FormEntryContext context) {
        for (Map.Entry<Obs, Set<Obs>> entry : context.getExistingObsInGroups().entrySet()) {
            Obs group = entry.getKey();

            // Skip if obs group isn't an immunization obs grouping
            if (!group.getConcept().equals(immunizationGroupConcept)) {
                continue;
            }

            Concept vaccineAnswer = null;
            Integer sequenceNumberAnswer = null;

            // Look through obs group members to find vaccine and sequence number
            for (Obs memberObs : entry.getValue()) {
                if (memberObs.getConcept().equals(vaccineQuestionConcept)) {
                    vaccineAnswer = memberObs.getValueCoded();
                }
                else if (memberObs.getConcept().equals(sequenceNumberConcept)) {
                    sequenceNumberAnswer = memberObs.getValueNumeric().intValue();
                }
            }

            // Remove and return and group if both vaccine and sequence number match
            if (OpenmrsUtil.nullSafeEquals(vaccineAnswer, vaccineConcept) && OpenmrsUtil.nullSafeEquals(sequenceNumberAnswer, sequenceNumber)) {
                context.getExistingObsInGroups().remove(group);
                return group;
            }
        }

        return null;
    }
}