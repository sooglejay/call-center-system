package com.callcenter.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.CreateTaskRequest
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.User
import com.callcenter.app.ui.viewmodel.CreateTaskViewModel

/**
 * 创建任务页面
 * 参考Web端体验，支持选择客服和分配客户
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateTaskScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateTaskViewModel = hiltViewModel()
) {
    val agents by viewModel.agents.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val nameLetterStats by viewModel.nameLetterStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val error by viewModel.error.collectAsState()

    // 表单状态
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedAgent by remember { mutableStateOf<User?>(null) }
    var priority by remember { mutableStateOf("normal") }
    var dueDate by remember { mutableStateOf("") }
    var showAgentDropdown by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }

    // 客户选择状态
    var selectedTab by remember { mutableStateOf(0) } // 0: 列表选择, 1: 字母选择
    var selectedCustomerIds by remember { mutableStateOf(setOf<Int>()) }
    var selectedLetters by remember { mutableStateOf(setOf<String>()) }
    var unassignedOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 字母表
    val alphabet = remember { ('A'..'Z').map { it.toString() } + "#" }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    // 当选择字母时，自动选择对应的客户
    LaunchedEffect(selectedLetters, customers) {
        if (selectedLetters.isNotEmpty()) {
            val letterCustomers = customers.filter { customer ->
                val firstLetter = getFirstLetter(customer.name)
                selectedLetters.contains(firstLetter)
            }
            selectedCustomerIds = selectedCustomerIds + letterCustomers.map { it.id }.toSet()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建任务") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            // 底部操作栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选择 ${selectedCustomerIds.size} 个客户",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                viewModel.setError("请输入任务标题")
                                return@Button
                            }
                            if (selectedAgent == null) {
                                viewModel.setError("请选择分配客服")
                                return@Button
                            }
                            if (selectedCustomerIds.isEmpty()) {
                                viewModel.setError("请至少选择一个客户")
                                return@Button
                            }
                            viewModel.createTask(
                                request = CreateTaskRequest(
                                    title = title,
                                    description = description.takeIf { it.isNotBlank() },
                                    priority = priority,
                                    assignedTo = selectedAgent?.id,
                                    dueDate = dueDate.takeIf { it.isNotBlank() },
                                    customerIds = selectedCustomerIds.toList()
                                ),
                                onSuccess = onNavigateBack
                            )
                        },
                        enabled = !isCreating
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("创建任务")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 错误提示
                        error?.let { errorMsg ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = errorMsg,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        // 基本信息区域
                        item {
                            Text(
                                text = "基本信息",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("任务标题 *") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("任务描述") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                        }

                        // 分配客服
                        item {
                            Text(
                                text = "分配客服 *",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Box {
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAgentDropdown = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        selectedAgent?.let { agent ->
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = agent.realName.take(1),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = agent.realName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "@${agent.username}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } ?: run {
                                            Text(
                                                text = "请选择客服",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }

                                DropdownMenu(
                                    expanded = showAgentDropdown,
                                    onDismissRequest = { showAgentDropdown = false }
                                ) {
                                    agents.forEach { agent ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = agent.realName.take(1),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(agent.realName)
                                                }
                                            },
                                            onClick = {
                                                selectedAgent = agent
                                                showAgentDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 优先级
                        item {
                            Text(
                                text = "优先级",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PriorityChip(
                                    label = "普通",
                                    selected = priority == "normal",
                                    color = Color(0xFF2196F3),
                                    onClick = { priority = "normal" }
                                )
                                PriorityChip(
                                    label = "高",
                                    selected = priority == "high",
                                    color = Color(0xFFFF9800),
                                    onClick = { priority = "high" }
                                )
                                PriorityChip(
                                    label = "紧急",
                                    selected = priority == "urgent",
                                    color = Color(0xFFE91E63),
                                    onClick = { priority = "urgent" }
                                )
                            }
                        }

                        // 客户选择区域
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "选择客户",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Tab切换
                        item {
                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("客户列表") }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("按姓氏选择") }
                                )
                            }
                        }

                        // 只显示未分配客户选项
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    unassignedOnly = !unassignedOnly
                                    viewModel.loadCustomers(unassignedOnly)
                                    viewModel.loadNameLetterStats(unassignedOnly)
                                }
                            ) {
                                Checkbox(
                                    checked = unassignedOnly,
                                    onCheckedChange = {
                                        unassignedOnly = it
                                        viewModel.loadCustomers(it)
                                        viewModel.loadNameLetterStats(it)
                                    }
                                )
                                Text("只显示未分配客户")
                            }
                        }

                        when (selectedTab) {
                            0 -> {
                                // 搜索框
                                item {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        label = { Text("搜索客户") },
                                        modifier = Modifier.fillMaxWidth(),
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = null)
                                        },
                                        singleLine = true
                                    )
                                }

                                // 客户列表
                                val filteredCustomers = customers.filter {
                                    it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.phone.contains(searchQuery)
                                }

                                if (filteredCustomers.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "暂无客户",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    items(filteredCustomers, key = { it.id }) { customer ->
                                        CustomerSelectItem(
                                            customer = customer,
                                            isSelected = selectedCustomerIds.contains(customer.id),
                                            onToggle = {
                                                selectedCustomerIds = if (selectedCustomerIds.contains(customer.id)) {
                                                    selectedCustomerIds - customer.id
                                                } else {
                                                    selectedCustomerIds + customer.id
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            1 -> {
                                // 字母选择
                                item {
                                    Text(
                                        text = "点击字母选择该姓氏的所有客户",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                item {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        alphabet.forEach { letter ->
                                            val count = nameLetterStats[letter] ?: 0
                                            val isSelected = selectedLetters.contains(letter)
                                            LetterChip(
                                                letter = letter,
                                                count = count,
                                                isSelected = isSelected,
                                                onClick = {
                                                    selectedLetters = if (isSelected) {
                                                        selectedLetters - letter
                                                    } else {
                                                        selectedLetters + letter
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // 已选择的字母
                                if (selectedLetters.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "已选择姓氏:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            selectedLetters.forEach { letter ->
                                                InputChip(
                                                    selected = true,
                                                    onClick = { selectedLetters = selectedLetters - letter },
                                                    label = { Text(letter) },
                                                    trailingIcon = {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "移除",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 底部留白
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, color) else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LetterChip(
    letter: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary
            count > 0 -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier
            .width(48.dp)
            .clickable(enabled = count > 0, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    count > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
            Text(
                text = if (count > 0) "$count" else "-",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    count > 0 -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
            )
        }
    }
}

@Composable
private fun CustomerSelectItem(
    customer: Customer,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                customer.company?.let { company ->
                    Text(
                        text = company,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (customer.assignedTo != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "已分配",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 获取姓氏首字母
 */
private fun getFirstLetter(name: String): String {
    if (name.isEmpty()) return "#"
    val firstChar = name.first()
    return if (firstChar.code in 0x4e00..0x9fff) {
        // 简单拼音映射（常用姓氏）
        val pinyinMap = mapOf(
            '阿' to "A", '艾' to "A", '安' to "A",
            '白' to "B", '班' to "B", '包' to "B", '鲍' to "B", '毕' to "B", '边' to "B",
            '蔡' to "C", '曹' to "C", '陈' to "C", '程' to "C", '崔' to "C",
            '戴' to "D", '邓' to "D", '丁' to "D", '董' to "D", '杜' to "D",
            '范' to "F", '方' to "F", '冯' to "F", '傅' to "F",
            '高' to "G", '葛' to "G", '郭' to "G",
            '韩' to "H", '何' to "H", '贺' to "H", '胡' to "H", '黄' to "H",
            '贾' to "J", '江' to "J", '姜' to "J", '蒋' to "J", '金' to "J",
            '康' to "K", '孔' to "K",
            '赖' to "L", '兰' to "L", '雷' to "L", '李' to "L", '梁' to "L",
            '林' to "L", '刘' to "L", '龙' to "L", '卢' to "L", '陆' to "L", '罗' to "L", '吕' to "L",
            '马' to "M", '毛' to "M", '孟' to "M", '莫' to "M",
            '潘' to "P", '彭' to "P",
            '钱' to "Q", '秦' to "Q", '邱' to "Q",
            '任' to "R",
            '沈' to "S", '史' to "S", '宋' to "S", '苏' to "S", '孙' to "S",
            '汤' to "T", '唐' to "T", '陶' to "T", '田' to "T",
            '万' to "W", '汪' to "W", '王' to "W", '韦' to "W", '魏' to "W",
            '吴' to "W", '武' to "W",
            '夏' to "X", '肖' to "X", '谢' to "X", '徐' to "X", '许' to "X",
            '严' to "Y", '杨' to "Y", '叶' to "Y", '易' to "Y", '殷' to "Y",
            '于' to "Y", '余' to "Y", '袁' to "Y",
            '曾' to "Z", '张' to "Z", '章' to "Z", '赵' to "Z", '郑' to "Z", '周' to "Z", '朱' to "Z"
        )
        pinyinMap[firstChar] ?: "#"
    } else {
        firstChar.uppercase()
    }
}
