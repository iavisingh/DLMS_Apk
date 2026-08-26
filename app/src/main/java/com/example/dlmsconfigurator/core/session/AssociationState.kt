package com.example.dlmsconfigurator.core.session

sealed class AssociationState {
    data object Idle : AssociationState()
    data object AarqSent : AssociationState()
    data object Associated : AssociationState()
    data object Releasing : AssociationState()
    data object Closed : AssociationState()
}
