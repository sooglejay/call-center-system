/**
 * 数据库种子脚本
 * 用于生成测试数据，方便开发和演示
 * 
 * 使用方法:
 *   npm run db:seed       # 生成完整测试数据
 *   npm run db:seed:mini  # 生成最小测试数据
 * 
 * 环境变量:
 *   DB_TYPE=sqlite        # 使用SQLite（默认）
 *   DB_TYPE=postgres      # 使用PostgreSQL
 */

import { query } from '../config/database';

// 生成日期辅助函数
const daysAgo = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().split('T')[0];
};

const now = () => new Date().toISOString();

// 中文姓氏拼音首字母映射
const getFirstLetter = (name: string): string => {
  if (!name || name.length === 0) return '#';
  
  const firstChar = name.charAt(0);
  const code = firstChar.charCodeAt(0);
  
  // 如果是英文，直接返回大写首字母
  if (code >= 65 && code <= 90) return firstChar;
  if (code >= 97 && code <= 122) return firstChar.toUpperCase();
  
  // 中文姓氏拼音首字母表
  const surnameMap: Record<string, string> = {
    '阿': 'A', '艾': 'A', '安': 'A', '奥': 'A',
    '巴': 'B', '白': 'B', '班': 'B', '包': 'B', '保': 'B', '鲍': 'B', '北': 'B', '贝': 'B', '毕': 'B', '边': 'B', '卞': 'B',
    '蔡': 'C', '曹': 'C', '岑': 'C', '常': 'C', '车': 'C', '陈': 'C', '成': 'C', '程': 'C', '池': 'C', '迟': 'C', '楚': 'C', '褚': 'C', '崔': 'C',
    '戴': 'D', '单': 'D', '邓': 'D', '狄': 'D', '丁': 'D', '董': 'D', '窦': 'D', '杜': 'D', '段': 'D', '端': 'D',
    '鄂': 'E', '恩': 'E',
    '樊': 'F', '范': 'F', '方': 'F', '房': 'F', '费': 'F', '冯': 'F', '符': 'F', '福': 'F', '付': 'F', '傅': 'F',
    '甘': 'G', '高': 'G', '葛': 'G', '耿': 'G', '龚': 'G', '公': 'G', '宫': 'G', '巩': 'G', '顾': 'G', '关': 'G', '官': 'G', '管': 'G', '桂': 'G', '郭': 'G', '国': 'G',
    '哈': 'H', '海': 'H', '韩': 'H', '杭': 'H', '郝': 'H', '何': 'H', '和': 'H', '贺': 'H', '赫': 'H', '衡': 'H', '洪': 'H', '侯': 'H', '后': 'H', '胡': 'H', '花': 'H', '华': 'H', '滑': 'H', '怀': 'H', '桓': 'H', '黄': 'H', '惠': 'H', '霍': 'H',
    '姬': 'J', '吉': 'J', '纪': 'J', '季': 'J', '贾': 'J', '简': 'J', '江': 'J', '姜': 'J', '蒋': 'J', '焦': 'J', '金': 'J', '靳': 'J', '景': 'J', '居': 'J', '鞠': 'J',
    '卡': 'K', '凯': 'K', '康': 'K', '柯': 'K', '孔': 'K', '寇': 'K', '况': 'K', '邝': 'K',
    '兰': 'L', '郎': 'L', '雷': 'L', '冷': 'L', '黎': 'L', '李': 'L', '厉': 'L', '利': 'L', '连': 'L', '廉': 'L', '梁': 'L', '廖': 'L', '林': 'L', '凌': 'L', '刘': 'L', '柳': 'L', '龙': 'L', '娄': 'L', '卢': 'L', '鲁': 'L', '陆': 'L', '路': 'L', '吕': 'L', '罗': 'L', '骆': 'L',
    '麻': 'M', '马': 'M', '麦': 'M', '满': 'M', '毛': 'M', '茅': 'M', '梅': 'M', '孟': 'M', '米': 'M', '苗': 'M', '明': 'M', '莫': 'M', '缪': 'M', '牟': 'M', '穆': 'M',
    '那': 'N', '南': 'N', '倪': 'N', '聂': 'N', '宁': 'N', '牛': 'N', '农': 'N', '欧': 'O', '区': 'O',
    '潘': 'P', '庞': 'P', '裴': 'P', '彭': 'P', '皮': 'P', '平': 'P', '蒲': 'P', '浦': 'P', '朴': 'P',
    '戚': 'Q', '齐': 'Q', '祁': 'Q', '钱': 'Q', '乔': 'Q', '秦': 'Q', '邱': 'Q', '裘': 'Q', '仇': 'Q', '屈': 'Q', '瞿': 'Q', '权': 'Q', '全': 'Q',
    '冉': 'R', '饶': 'R', '任': 'R', '荣': 'R', '阮': 'R', '芮': 'R',
    '桑': 'S', '沙': 'S', '单': 'S', '商': 'S', '尚': 'S', '邵': 'S', '申': 'S', '沈': 'S', '盛': 'S', '施': 'S', '石': 'S', '史': 'S', '司': 'S', '宋': 'S', '苏': 'S', '孙': 'S', '索': 'S',
    '台': 'T', '太': 'T', '谈': 'T', '谭': 'T', '汤': 'T', '唐': 'T', '陶': 'T', '田': 'T', '童': 'T', '涂': 'T', '屠': 'T',
    '万': 'W', '汪': 'W', '王': 'W', '韦': 'W', '卫': 'W', '魏': 'W', '温': 'W', '文': 'W', '闻': 'W', '翁': 'W', '沃': 'W', '乌': 'W', '邬': 'W', '巫': 'W', '吴': 'W', '武': 'W', '伍': 'W',
    '西': 'X', '席': 'X', '夏': 'X', '相': 'X', '向': 'X', '萧': 'X', '谢': 'X', '邢': 'X', '幸': 'X', '熊': 'X', '徐': 'X', '许': 'X', '宣': 'X', '薛': 'X', '荀': 'X',
    '雅': 'Y', '严': 'Y', '颜': 'Y', '晏': 'Y', '阳': 'Y', '杨': 'Y', '仰': 'Y', '姚': 'Y', '叶': 'Y', '易': 'Y', '殷': 'Y', '尹': 'Y', '印': 'Y', '应': 'Y', '尤': 'Y', '游': 'Y', '于': 'Y', '余': 'Y', '俞': 'Y', '虞': 'Y', '宇': 'Y', '禹': 'Y', '郁': 'Y', '喻': 'Y', '元': 'Y', '袁': 'Y', '岳': 'Y', '云': 'Y', '郓': 'Y',
    '藏': 'Z', '宰': 'Z', '昝': 'Z', '臧': 'Z', '湛': 'Z', '张': 'Z', '章': 'Z', '赵': 'Z', '甄': 'Z', '郑': 'Z', '支': 'Z', '钟': 'Z', '仲': 'Z', '周': 'Z', '朱': 'Z', '诸': 'Z', '诸葛': 'Z', '祝': 'Z', '庄': 'Z', '卓': 'Z', '邹': 'Z', '祖': 'Z', '左': 'Z'
  };
  
  return surnameMap[firstChar] || '#';
};

// ============ 测试数据 ============

// 扩展的用户数据
const users = [
  {
    username: 'admin',
    password: 'admin123', // 明文密码（开发便利）
    role: 'admin',
    real_name: '系统管理员',
    phone: '13800000000',
    email: 'admin@callcenter.com',
    department: '管理部',
    position: '系统管理员',
    status: 'active'
  },
  {
    username: 'agent01',
    password: 'agent123', // 明文密码（开发便利）
    role: 'agent',
    real_name: '张小明',
    phone: '13800138001',
    email: 'zhangxm@callcenter.com',
    department: '客服部',
    position: '高级客服专员',
    status: 'active'
  },
  {
    username: 'agent02',
    password: 'agent123',
    role: 'agent',
    real_name: '李晓红',
    phone: '13800138002',
    email: 'lixh@callcenter.com',
    department: '客服部',
    position: '客服专员',
    status: 'active'
  },
  {
    username: 'agent03',
    password: 'agent123',
    role: 'agent',
    real_name: '王建国',
    phone: '13800138003',
    email: 'wangjg@callcenter.com',
    department: '销售部',
    position: '销售主管',
    status: 'active'
  },
  {
    username: 'agent04',
    password: 'agent123',
    role: 'agent',
    real_name: '刘芳华',
    phone: '13800138004',
    email: 'liufh@callcenter.com',
    department: '客服部',
    position: '客服专员',
    status: 'active'
  }
];

// 扩展的客户数据（按姓氏首字母分布）
const customers = [
  // A
  { name: '艾美丽', phone: '13900000001', email: 'ai@example.com', company: '爱美科技', address: '北京市朝阳区建国路1号', notes: 'VIP客户，需要优先跟进', status: 'interested', priority: 1 },
  { name: '安志强', phone: '13900000002', email: 'an@example.com', company: '安盛集团', address: '上海市浦东新区陆家嘴2号', notes: '对产品很感兴趣', status: 'pending', priority: 2 },
  
  // B
  { name: '白晓明', phone: '13900000003', email: 'bai@example.com', company: '白云贸易', address: '广州市天河区珠江新城3号', notes: '需要报价单', status: 'contacted', priority: 1 },
  { name: '包青天', phone: '13900000004', email: 'bao@example.com', company: '包氏企业', address: '深圳市南山区科技园4号', notes: '大型企业，采购量大', status: 'pending', priority: 1 },
  { name: '毕成功', phone: '13900000005', email: 'bi@example.com', company: '必胜科技', address: '杭州市西湖区文三路5号', notes: '正在比较竞品', status: 'interested', priority: 2 },
  
  // C
  { name: '陈大明', phone: '13900000006', email: 'chen@example.com', company: '晨光文具', address: '成都市高新区天府大道6号', notes: '老客户推荐', status: 'converted', priority: 1 },
  { name: '蔡美玲', phone: '13900000007', email: 'cai@example.com', company: '彩虹传媒', address: '武汉市江汉区解放大道7号', notes: '媒体行业，推广需求', status: 'pending', priority: 2 },
  { name: '曹国伟', phone: '13900000008', email: 'cao@example.com', company: '国泰软件', address: '南京市鼓楼区中山路8号', notes: '技术负责人，需要演示', status: 'contacted', priority: 1 },
  { name: '常胜利', phone: '13900000009', email: 'chang@example.com', company: '常青建设', address: '西安市雁塔区长安路9号', notes: '建筑行业客户', status: 'not_interested', priority: 3 },
  
  // D
  { name: '丁志强', phone: '13900000010', email: 'ding@example.com', company: '鼎盛实业', address: '重庆市渝中区解放碑10号', notes: '高价值客户', status: 'interested', priority: 1 },
  { name: '董建华', phone: '13900000011', email: 'dong@example.com', company: '东方航空', address: '天津市和平区南京路11号', notes: '国有企业，流程较长', status: 'pending', priority: 2 },
  { name: '杜小月', phone: '13900000012', email: 'du@example.com', company: '杜鹃教育', address: '苏州市工业园区星湖街12号', notes: '教育机构，批量采购', status: 'contacted', priority: 1 },
  
  // F
  { name: '方大同', phone: '13900000013', email: 'fang@example.com', company: '方圆地产', address: '青岛市市南区香港路13号', notes: '房地产行业', status: 'pending', priority: 2 },
  { name: '冯巩', phone: '13900000014', email: 'feng@example.com', company: '凤凰传媒', address: '宁波市鄞州区天童路14号', notes: '需要后续跟进', status: 'pending', priority: 3 },
  { name: '范伟', phone: '13900000015', email: 'fan@example.com', company: '范氏集团', address: '厦门市思明区鹭江道15号', notes: '已有初步意向', status: 'interested', priority: 2 },
  
  // G
  { name: '郭德纲', phone: '13900000016', email: 'guo@example.com', company: '德云社', address: '北京市西城区前门大街16号', notes: '文化传媒行业', status: 'pending', priority: 2 },
  { name: '高圆圆', phone: '13900000017', email: 'gao@example.com', company: '高氏投资', address: '上海市黄浦区外滩17号', notes: '投资公司，关注ROI', status: 'interested', priority: 1 },
  { name: '郭晶晶', phone: '13900000018', email: 'guo2@example.com', company: '国泰体育', address: '广州市越秀区体育西路18号', notes: '体育用品行业', status: 'contacted', priority: 2 },
  { name: '葛优等', phone: '13900000019', email: 'ge@example.com', company: '葛优影视', address: '深圳市福田区华强北19号', notes: '影视公司', status: 'pending', priority: 3 },
  
  // H
  { name: '黄晓明', phone: '13900000020', email: 'huang@example.com', company: '华谊影视', address: '杭州市江干区钱江新城20号', notes: '娱乐圈客户', status: 'interested', priority: 1 },
  { name: '何炅', phone: '13900000021', email: 'he@example.com', company: '快乐传媒', address: '长沙市天心区湘江中路21号', notes: '综艺节目制作', status: 'contacted', priority: 2 },
  { name: '韩寒', phone: '13900000022', email: 'han@example.com', company: '韩寒文化', address: '上海市徐汇区衡山路22号', notes: '作家，文化公司', status: 'pending', priority: 2 },
  { name: '霍思燕', phone: '13900000023', email: 'huo@example.com', company: '燕氏美容', address: '北京市朝阳区三里屯23号', notes: '美容行业', status: 'pending', priority: 3 },
  { name: '胡军', phone: '13900000024', email: 'hu@example.com', company: '胡氏贸易', address: '天津市滨海新区24号', notes: '贸易公司', status: 'not_interested', priority: 3 },
  
  // J
  { name: '周杰伦', phone: '13900000025', email: 'zhou@example.com', company: '杰威尔音乐', address: '台北市信义区松仁路25号', notes: '音乐制作公司', status: 'interested', priority: 1 },
  { name: '姜文', phone: '13900000026', email: 'jiang@example.com', company: '阳光灿烂', address: '北京市朝阳区酒仙桥26号', notes: '电影制作公司', status: 'contacted', priority: 1 },
  { name: '贾玲', phone: '13900000027', email: 'jia@example.com', company: '大碗娱乐', address: '上海市静安区南京西路27号', notes: '喜剧制作公司', status: 'pending', priority: 2 },
  { name: '金秀贤', phone: '13900000028', email: 'jin@example.com', company: '金氏韩流', address: '青岛市城阳区28号', notes: '韩国客户代表', status: 'pending', priority: 2 },
  
  // K
  { name: ' Kardashian', phone: '13900000029', email: 'kardashian@example.com', company: '卡戴珊集团', address: '洛杉矶比佛利山庄29号', notes: '国际品牌', status: 'interested', priority: 1 },
  
  // L
  { name: '刘德华', phone: '13900000030', email: 'liu@example.com', company: '映艺娱乐', address: '香港九龙尖沙咀30号', notes: '影视巨星', status: 'interested', priority: 1 },
  { name: '李彦宏', phone: '13900000031', email: 'li@example.com', company: '百度科技', address: '北京市海淀区上地31号', notes: '科技公司CEO', status: 'contacted', priority: 1 },
  { name: '李小璐', phone: '13900000032', email: 'li2@example.com', company: '璐璐工作室', address: '北京市朝阳区国贸32号', notes: '明星工作室', status: 'pending', priority: 2 },
  { name: '林志玲', phone: '13900000033', email: 'lin@example.com', company: '志玲慈善', address: '台北市大安区33号', notes: '慈善基金会', status: 'pending', priority: 2 },
  { name: '李连杰', phone: '13900000034', email: 'li3@example.com', company: '壹基金', address: '北京市东城区34号', notes: '慈善机构', status: 'interested', priority: 1 },
  { name: '李宇春', phone: '13900000035', email: 'li4@example.com', company: '玉米音乐', address: '成都市武侯区35号', notes: '音乐制作', status: 'contacted', priority: 2 },
  
  // M
  { name: '马云', phone: '13900000036', email: 'ma@example.com', company: '阿里巴巴集团', address: '杭州市余杭区文一西路36号', notes: '互联网巨头', status: 'converted', priority: 1 },
  { name: '马化腾', phone: '13900000037', email: 'ma2@example.com', company: '腾讯科技', address: '深圳市南山区深南大道37号', notes: '科技公司', status: 'contacted', priority: 1 },
  { name: '杨幂', phone: '13900000038', email: 'yang@example.com', company: '嘉行传媒', address: '北京市朝阳区望京38号', notes: '影视制作', status: 'interested', priority: 1 },
  { name: '莫言', phone: '13900000039', email: 'mo@example.com', company: '莫言文学', address: '北京市海淀区39号', notes: '诺贝尔文学奖得主', status: 'pending', priority: 2 },
  
  // P
  { name: '潘长江', phone: '13900000040', email: 'pan@example.com', company: '潘氏喜剧', address: '长春市朝阳区40号', notes: '喜剧演员', status: 'pending', priority: 3 },
  { name: '彭于晏', phone: '13900000041', email: 'peng@example.com', company: '晏晏工作室', address: '台北市信义区41号', notes: '演员工作室', status: 'contacted', priority: 2 },
  
  // S
  { name: '孙红雷', phone: '13900000042', email: 'sun@example.com', company: '雷雷影视', address: '北京市朝阳区42号', notes: '演员', status: 'interested', priority: 2 },
  { name: '孙俪', phone: '13900000043', email: 'sun2@example.com', company: '俪人工作室', address: '上海市徐汇区43号', notes: '演员', status: 'pending', priority: 2 },
  { name: '宋茜', phone: '13900000044', email: 'song@example.com', company: '宋茜娱乐', address: '北京市朝阳区44号', notes: '歌手演员', status: 'pending', priority: 3 },
  { name: '舒淇', phone: '13900000045', email: 'shu@example.com', company: '淇淇影业', address: '香港中环45号', notes: '电影制作', status: 'contacted', priority: 2 },
  
  // T
  { name: '汤唯', phone: '13900000046', email: 'tang@example.com', company: '唯唯影业', address: '北京市朝阳区46号', notes: '演员', status: 'interested', priority: 2 },
  { name: '唐国强', phone: '13900000047', email: 'tang2@example.com', company: '国强影视', address: '北京市西城区47号', notes: '演员', status: 'pending', priority: 3 },
  { name: '佟大为', phone: '13900000048', email: 'tong@example.com', company: '大为传媒', address: '上海市黄浦区48号', notes: '演员', status: 'pending', priority: 2 },
  
  // W
  { name: '王力宏', phone: '13900000049', email: 'wang@example.com', company: '宏声音乐', address: '台北市大安区49号', notes: '音乐人', status: 'interested', priority: 1 },
  { name: '吴亦凡', phone: '13900000050', email: 'wu@example.com', company: '凡凡工作室', address: '北京市朝阳区50号', notes: '艺人工作室', status: 'not_interested', priority: 3 },
  { name: '吴京', phone: '13900000051', email: 'wu2@example.com', company: '登峰国际', address: '北京市怀柔区51号', notes: '电影制作', status: 'contacted', priority: 1 },
  { name: '王宝强', phone: '13900000052', email: 'wang2@example.com', company: '宝强影业', address: '北京市朝阳区52号', notes: '演员导演', status: 'pending', priority: 2 },
  { name: '王思聪', phone: '13900000053', email: 'wang3@example.com', company: '普思投资', address: '北京市朝阳区53号', notes: '投资公司', status: 'interested', priority: 1 },
  
  // X
  { name: '徐峥', phone: '13900000054', email: 'xu@example.com', company: '真乐道', address: '北京市朝阳区54号', notes: '导演演员', status: 'interested', priority: 1 },
  { name: '谢霆锋', phone: '13900000055', email: 'xie@example.com', company: '锋味控股', address: '香港中环55号', notes: '餐饮品牌', status: 'contacted', priority: 2 },
  { name: '萧敬腾', phone: '13900000056', email: 'xiao@example.com', company: '狮子音乐', address: '台北市信义区56号', notes: '音乐制作', status: 'pending', priority: 2 },
  
  // Y
  { name: '杨千嬅', phone: '13900000057', email: 'yang2@example.com', company: '千嬅工作室', address: '香港铜锣湾57号', notes: '歌手演员', status: 'interested', priority: 2 },
  { name: '杨幂', phone: '13900000058', email: 'yang3@example.com', company: '幂幂影视', address: '北京市朝阳区58号', notes: '影视制作', status: 'pending', priority: 1 },
  { name: '余文乐', phone: '13900000059', email: 'yu@example.com', company: 'MADNESS', address: '香港尖沙咀59号', notes: '潮流品牌', status: 'contacted', priority: 2 },
  { name: '姚明', phone: '13900000060', email: 'yao@example.com', company: '姚基金', address: '上海市徐汇区60号', notes: '慈善基金', status: 'interested', priority: 1 },
  
  // Z
  { name: '张艺谋', phone: '13900000061', email: 'zhang@example.com', company: '印象文化', address: '北京市朝阳区61号', notes: '导演', status: 'interested', priority: 1 },
  { name: '周杰伦', phone: '13900000062', email: 'zhou2@example.com', company: '周氏音乐', address: '台北市信义区62号', notes: '音乐制作', status: 'contacted', priority: 1 },
  { name: '赵丽颖', phone: '13900000063', email: 'zhao@example.com', company: '颖宝工作室', address: '北京市朝阳区63号', notes: '演员', status: 'pending', priority: 2 },
  { name: '赵薇', phone: '13900000064', email: 'zhao2@example.com', company: '薇娅文化', address: '北京市朝阳区64号', notes: '导演演员', status: 'pending', priority: 2 },
  { name: '张学友', phone: '13900000065', email: 'zhang2@example.com', company: '友友音乐', address: '香港红磡65号', notes: '歌神', status: 'interested', priority: 1 },
  { name: '张国立', phone: '13900000066', email: 'zhang3@example.com', company: '国立影视', address: '北京市海淀区66号', notes: '演员', status: 'contacted', priority: 2 },
  { name: '章子怡', phone: '13900000067', email: 'zhang4@example.com', company: '怡怡影业', address: '北京市朝阳区67号', notes: '演员', status: 'pending', priority: 1 }
];

// 通话记录数据
const callResults = ['answered', 'no-answer', 'busy', 'voicemail', 'failed'];
const generateCalls = (customerCount: number) => {
  const calls = [];
  for (let i = 0; i < 30; i++) {
    const customerId = Math.floor(Math.random() * customerCount) + 1;
    const agentId = Math.floor(Math.random() * 3) + 2; // 2,3,4
    const result = callResults[Math.floor(Math.random() * callResults.length)];
    const daysBack = Math.floor(Math.random() * 14);
    
    calls.push({
      customer_id: customerId,
      agent_id: agentId,
      customer_phone: `139${String(customerId).padStart(8, '0')}`,
      status: 'completed',
      call_result: result,
      call_notes: `通话结果：${result}，客户反馈${['很好', '一般', '需要再考虑', '价格偏高', '需要演示'][Math.floor(Math.random() * 5)]}`,
      recording_duration: result === 'answered' ? Math.floor(Math.random() * 300) + 60 : 0,
      started_at: daysAgo(daysBack),
      ended_at: daysAgo(daysBack),
      is_connected: result === 'answered'
    });
  }
  return calls;
};

// 任务数据
const generateTasks = (customerCount: number) => {
  const priorities = ['urgent', 'high', 'normal', 'low'];
  const statuses = ['pending', 'in_progress', 'completed'];
  const titles = [
    '跟进客户需求', '发送产品资料', '安排产品演示', '准备报价单',
    '合同签署跟进', '售后服务回访', '客户满意度调查', '续费提醒',
    '新产品推荐', '投诉处理', '账户问题处理', '功能培训安排'
  ];
  
  const tasks = [];
  for (let i = 0; i < 20; i++) {
    const customerId = Math.random() > 0.3 ? Math.floor(Math.random() * customerCount) + 1 : null;
    const agentId = Math.floor(Math.random() * 3) + 2;
    const status = statuses[Math.floor(Math.random() * statuses.length)];
    const daysDue = Math.floor(Math.random() * 14) - 7; // -7 到 7 天
    
    tasks.push({
      title: titles[Math.floor(Math.random() * titles.length)],
      description: `任务详情描述，需要${['尽快处理', '按时完成', '优先跟进', '详细记录'][Math.floor(Math.random() * 4)]}`,
      assigned_to: agentId,
      customer_id: customerId,
      priority: priorities[Math.floor(Math.random() * priorities.length)],
      status: status,
      due_date: daysAgo(-daysDue),
      completed_at: status === 'completed' ? daysAgo(Math.floor(Math.random() * 5)) : null,
      created_by: 1
    });
  }
  return tasks;
};

// ============ 种子函数 ============

async function seedUsers() {
  console.log('📝 插入用户数据...');
  // 首先清理现有用户（除了默认admin/agent）
  await query("DELETE FROM users WHERE id > 2", []);
  // 重置自增ID
  await query("DELETE FROM sqlite_sequence WHERE name = 'users'", []);
  
  // 确保默认用户ID正确
  await query(
    "UPDATE users SET id = 1 WHERE username = 'admin'",
    []
  );
  await query(
    "UPDATE users SET id = 2 WHERE username = 'agent'",
    []
  );
  
  for (const user of users) {
    try {
      // 跳过默认用户（已存在）
      const existing = await query('SELECT id FROM users WHERE username = $1', [user.username]);
      if (existing.rows.length > 0) {
        continue;
      }
      
      await query(
        `INSERT INTO users (username, password, role, real_name, phone, email, department, position, status, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, datetime('now'), datetime('now'))`,
        [user.username, user.password, user.role, user.real_name, user.phone, user.email, user.department, user.position, user.status]
      );
    } catch (error) {
      console.error(`插入用户 ${user.username} 失败:`, error);
    }
  }
  console.log(`✅ 已插入 ${users.length} 个用户`);
}

async function seedCustomers() {
  console.log('📝 插入客户数据...');
  let count = 0;
  for (const customer of customers) {
    try {
      const assignedTo = Math.random() > 0.3 ? Math.floor(Math.random() * 3) + 2 : null;
      await query(
        `INSERT INTO customers (name, phone, email, company, address, notes, status, priority, assigned_to, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, datetime('now', '-${Math.floor(Math.random() * 30)} days'), datetime('now'))`,
        [customer.name, customer.phone, customer.email, customer.company, customer.address, customer.notes, customer.status, customer.priority, assignedTo]
      );
      count++;
    } catch (error) {
      console.error(`插入客户 ${customer.name} 失败:`, error);
    }
  }
  console.log(`✅ 已插入 ${count} 个客户`);
  return count;
}

async function seedCalls(customerCount: number) {
  console.log('📝 插入通话记录...');
  const calls = generateCalls(customerCount);
  let count = 0;
  for (const call of calls) {
    try {
      await query(
        `INSERT INTO calls (customer_id, agent_id, customer_phone, status, call_result, call_notes, recording_duration, started_at, ended_at, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, datetime($8), datetime($9), datetime('now'), datetime('now'))`,
        [call.customer_id, call.agent_id, call.customer_phone, call.status, call.call_result, call.call_notes, call.recording_duration, call.started_at, call.ended_at]
      );
      count++;
    } catch (error) {
      console.error('插入通话记录失败:', error);
    }
  }
  console.log(`✅ 已插入 ${count} 条通话记录`);
}

async function seedTasks(customerCount: number) {
  console.log('📝 插入任务数据...');
  const tasks = generateTasks(customerCount);
  let count = 0;
  for (const task of tasks) {
    try {
      await query(
        `INSERT INTO tasks (title, description, assigned_to, customer_id, priority, status, due_date, completed_at, created_by, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, datetime('now'), datetime('now'))`,
        [task.title, task.description, task.assigned_to, task.customer_id, task.priority, task.status, task.due_date, task.completed_at, task.created_by]
      );
      count++;
    } catch (error) {
      console.error('插入任务失败:', error);
    }
  }
  console.log(`✅ 已插入 ${count} 个任务`);
}

async function seedAgentConfigs() {
  console.log('📝 插入客服配置...');
  const configs = [
    { agent_id: 2, auto_dial_enabled: 1, dial_interval: 30, dial_start_time: '09:00:00', dial_end_time: '18:00:00' },
    { agent_id: 3, auto_dial_enabled: 0, dial_interval: 60, dial_start_time: '08:30:00', dial_end_time: '17:30:00' },
    { agent_id: 4, auto_dial_enabled: 1, dial_interval: 45, dial_start_time: '09:30:00', dial_end_time: '18:30:00' }
  ];
  
  for (const config of configs) {
    try {
      await query(
        `INSERT OR REPLACE INTO agent_configs (agent_id, auto_dial_enabled, dial_interval, dial_start_time, dial_end_time, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, datetime('now'), datetime('now'))`,
        [config.agent_id, config.auto_dial_enabled, config.dial_interval, config.dial_start_time, config.dial_end_time]
      );
    } catch (error) {
      console.error(`插入客服配置 ${config.agent_id} 失败:`, error);
    }
  }
  console.log(`✅ 已插入 ${configs.length} 个客服配置`);
}

async function clearData() {
  console.log('🧹 清理现有数据...');
  const tables = ['unanswered_records', 'sms_records', 'voicemail_records', 'tasks', 'calls', 'agent_configs', 'customers'];
  for (const table of tables) {
    try {
      await query(`DELETE FROM ${table}`, []);
    } catch (error) {
      console.error(`清理表 ${table} 失败:`, error);
    }
  }
  // 重置自增ID
  for (const table of tables) {
    try {
      await query(`DELETE FROM sqlite_sequence WHERE name = $1`, [table]);
    } catch (error) {
      // 忽略错误，表可能没有自增序列
    }
  }
  console.log('✅ 数据清理完成');
}

async function showStats() {
  console.log('\n📊 数据库统计:');
  const tables = ['users', 'customers', 'calls', 'tasks', 'voicemail_records', 'sms_records', 'unanswered_records'];
  for (const table of tables) {
    try {
      const result = await query(`SELECT COUNT(*) as count FROM ${table}`, []);
      console.log(`   ${table}: ${result.rows[0]?.count || 0} 条记录`);
    } catch (error) {
      console.error(`统计表 ${table} 失败:`, error);
    }
  }
}

// ============ 主函数 ============

async function seed(isMini = false) {
  console.log('🌱 开始生成测试数据...\n');
  
  try {
    // 清理数据（保留用户）
    await clearData();
    
    // 插入用户
    await seedUsers();
    
    // 插入客户
    const customerCount = await seedCustomers();
    
    if (!isMini) {
      // 插入通话记录
      await seedCalls(customerCount);
      
      // 插入任务
      await seedTasks(customerCount);
      
      // 插入客服配置
      await seedAgentConfigs();
    }
    
    // 显示统计
    await showStats();
    
    console.log('\n✨ 测试数据生成完成！');
    console.log('\n默认登录账号:');
    console.log('  管理员: admin / admin123');
    console.log('  客服1:  agent01 / agent123');
    console.log('  客服2:  agent02 / agent123');
    console.log('  客服3:  agent03 / agent123');
    
  } catch (error) {
    console.error('❌ 生成测试数据失败:', error);
    process.exit(1);
  }
  
  process.exit(0);
}

// 检查命令行参数
const isMini = process.argv.includes('--mini') || process.argv.includes('-m');
seed(isMini);
