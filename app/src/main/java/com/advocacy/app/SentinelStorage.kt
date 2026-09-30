
            ScreenState.REGISTER_CONTACT -> ContactRegistrationScreen(
                initialName = payload.contactName,
                initialPhone = payload.contactPhone,
                onSave = { name, phone ->
                    val updated = payload.copy(
                        contactName = name,
                        contactPhone = phone,
                        lastModified = System.currentTimeMillis()
                    )
                    storageManager.savePayload(updated)
                    payload = updated
                    Toast.makeText(context, "Emergency contact saved locally", Toast.LENGTH_SHORT).show()
                    currentScreen = ScreenState.INPUT
                },
                onCancel = { currentScreen = ScreenState.INPUT }
            )
