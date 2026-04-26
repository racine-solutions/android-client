/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-field-officer-app/blob/master/LICENSE.md
 */
package com.mifos.feature.loan.loanApproval

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mifos.core.common.utils.DataState
import com.mifos.core.data.repository.LoanAccountApprovalRepository
import com.mifos.room.entities.accounts.loans.LoanApprovalData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class LoanAccountApprovalViewModel(
    private val repository: LoanAccountApprovalRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val log = Logger.withTag(TAG)

    private val arg = savedStateHandle.getStateFlow(key = "arg", initialValue = "")
    private val loanAccountData: LoanApprovalData = Json.decodeFromString<LoanApprovalData>(arg.value)

    private val _loanAccountApprovalUiState =
        MutableStateFlow<LoanAccountApprovalUiState>(LoanAccountApprovalUiState.Initial)
    val loanAccountApprovalUiState: StateFlow<LoanAccountApprovalUiState> get() = _loanAccountApprovalUiState

    var loanId = loanAccountData.loanID
    var loanWithAssociations = loanAccountData.loanWithAssociations

    fun approveLoan(loanApproval: com.mifos.core.model.objects.account.loan.LoanApproval?) {
        log.i {
            "approveLoan() called: loanId=$loanId, " +
                "approvedOnDate=${loanApproval?.approvedOnDate}, " +
                "approvedLoanAmount=${loanApproval?.approvedLoanAmount}, " +
                "expectedDisbursementDate=${loanApproval?.expectedDisbursementDate}, " +
                "note=${loanApproval?.note}, " +
                "locale=${loanApproval?.locale}, " +
                "dateFormat=${loanApproval?.dateFormat}"
        }

        viewModelScope.launch {
            repository.approveLoan(loanId, loanApproval)
                .catch { throwable ->
                    log.e(throwable) { "approveLoan() flow threw: ${throwable.message}" }
                    _loanAccountApprovalUiState.value =
                        LoanAccountApprovalUiState.ShowLoanApproveFailed(
                            throwable.message ?: "Unknown error",
                        )
                }
                .collect { dataState ->
                    when (dataState) {
                        is DataState.Loading -> {
                            log.d { "approveLoan() loading…" }
                            _loanAccountApprovalUiState.value =
                                LoanAccountApprovalUiState.ShowProgressbar
                        }

                        is DataState.Success -> {
                            log.i { "approveLoan() success: response=${dataState.data}" }
                            _loanAccountApprovalUiState.value =
                                LoanAccountApprovalUiState.ShowLoanApproveSuccessfully(dataState.data)
                        }

                        is DataState.Error -> {
                            log.e(dataState.exception) {
                                "approveLoan() failed: ${dataState.message}"
                            }
                            _loanAccountApprovalUiState.value =
                                LoanAccountApprovalUiState.ShowLoanApproveFailed(
                                    dataState.message,
                                )
                        }
                    }
                }
        }
    }

    private companion object {
        const val TAG = "LoanApproval"
    }
}
