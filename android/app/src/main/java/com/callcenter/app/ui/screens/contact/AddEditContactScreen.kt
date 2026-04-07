package com.callcenter.app.ui.screens.contact

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.local.entity.ContactEntity
import com.callcenter.app.ui.viewmodel.ContactViewModel

/**
 * 添加/编辑联系人页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditContactScreen(
    contact: ContactEntity? = null,
    onNavigateBack: () -> Unit,
    contactViewModel: ContactViewModel = hiltViewModel()
) {
    val isEditing = contact != null

    var name by remember { mutableStateOf(contact?.name ?: "") }
    var phone by remember { mutableStateOf(contact?.phone ?: "") }
    var email by remember { mutableStateOf(contact?.email ?: "") }
    var company by remember { mutableStateOf(contact?.company ?: "") }
    var notes by remember { mutableStateOf(contact?.notes ?: "") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    val error by contactViewModel.error.collectAsState()

    // 显示错误提示
    LaunchedEffect(error) {
        error?.let {
            // 错误已在 ViewModel 中处理，这里可以添加 Toast 提示
        }
    }

    fun validate(): Boolean {
        var isValid = true

        if (name.isBlank()) {
            nameError = "请输入姓名"
            isValid = false
        } else {
            nameError = null
        }

        if (phone.isBlank()) {
            phoneError = "请输入电话号码"
            isValid = false
        } else if (!phone.matches(Regex("^[0-9+*#]+$"))) {
            phoneError = "电话号码格式不正确"
            isValid = false
        } else {
            phoneError = null
        }

        return isValid
    }

    fun saveContact() {
        if (!validate()) return

        if (isEditing) {
            contact?.let {
                val updatedContact = it.copy(
                    name = name.trim(),
                    phone = phone.trim(),
                    email = email.trim().takeIf { it.isNotEmpty() },
                    company = company.trim().takeIf { it.isNotEmpty() },
                    notes = notes.trim().takeIf { it.isNotEmpty() }
                )
                contactViewModel.updateContact(updatedContact)
            }
        } else {
            contactViewModel.addContact(
                name = name.trim(),
                phone = phone.trim(),
                email = email.trim().takeIf { it.isNotEmpty() },
                company = company.trim().takeIf { it.isNotEmpty() },
                notes = notes.trim().takeIf { it.isNotEmpty() }
            )
        }
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑联系人" else "添加联系人") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { saveContact() },
                        enabled = name.isNotBlank() && phone.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 姓名
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("姓名 *") },
                placeholder = { Text("请输入姓名") },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                shape = RoundedCornerShape(8.dp)
            )

            // 电话
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; phoneError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("电话 *") },
                placeholder = { Text("请输入电话号码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it) } },
                shape = RoundedCornerShape(8.dp)
            )

            // 邮箱
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                placeholder = { Text("请输入邮箱（选填）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(8.dp)
            )

            // 公司
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("公司") },
                placeholder = { Text("请输入公司名称（选填）") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // 备注
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                placeholder = { Text("请输入备注（选填）") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 保存按钮
            Button(
                onClick = { saveContact() },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && phone.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isEditing) "保存修改" else "添加联系人",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(8.dp))

                // 删除按钮
                OutlinedButton(
                    onClick = {
                        contact?.let {
                            contactViewModel.deleteContact(it)
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "删除联系人",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
