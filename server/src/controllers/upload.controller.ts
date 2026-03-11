import { Request, Response } from 'express';
import multer from 'multer';
import xlsx from 'xlsx';
import csv from 'csv-parser';
import fs from 'fs';
import path from 'path';

const upload = multer({ dest: 'uploads/' });

export const uploadMiddleware = upload.single('file');

export const uploadFile = async (req: Request, res: Response) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '未上传文件' });
    }
    
    const filePath = req.file.path;
    const fileExt = path.extname(req.file.originalname).toLowerCase();
    
    let customers: any[] = [];
    
    if (fileExt === '.xlsx' || fileExt === '.xls') {
      customers = await parseExcel(filePath);
    } else if (fileExt === '.csv') {
      customers = await parseCSV(filePath);
    } else if (fileExt === '.txt') {
      customers = await parseTXT(filePath);
    } else {
      fs.unlinkSync(filePath);
      return res.status(400).json({ error: '不支持的文件格式' });
    }
    
    // 清理临时文件
    fs.unlinkSync(filePath);
    
    res.json({
      message: `成功解析 ${customers.length} 条记录`,
      data: customers
    });
  } catch (error) {
    console.error('上传文件错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

const parseExcel = (filePath: string): Promise<any[]> => {
  return new Promise((resolve, reject) => {
    try {
      const workbook = xlsx.readFile(filePath);
      const sheetName = workbook.SheetNames[0];
      const worksheet = workbook.Sheets[sheetName];
      const data = xlsx.utils.sheet_to_json(worksheet);
      
      const customers = data.map((row: any) => ({
        phone: String(row['电话号码'] || row['phone'] || row['电话'] || row['手机号'] || ''),
        name: String(row['客户姓名'] || row['name'] || row['姓名'] || ''),
        remark: String(row['备注'] || row['remark'] || row['客户备注'] || '')
      })).filter(c => c.phone);
      
      resolve(customers);
    } catch (error) {
      reject(error);
    }
  });
};

const parseCSV = (filePath: string): Promise<any[]> => {
  return new Promise((resolve, reject) => {
    const customers: any[] = [];
    
    fs.createReadStream(filePath)
      .pipe(csv())
      .on('data', (row: any) => {
        customers.push({
          phone: String(row['电话号码'] || row['phone'] || row['电话'] || row['手机号'] || ''),
          name: String(row['客户姓名'] || row['name'] || row['姓名'] || ''),
          remark: String(row['备注'] || row['remark'] || row['客户备注'] || '')
        });
      })
      .on('end', () => {
        resolve(customers.filter(c => c.phone));
      })
      .on('error', reject);
  });
};

const parseTXT = (filePath: string): Promise<any[]> => {
  return new Promise((resolve, reject) => {
    fs.readFile(filePath, 'utf-8', (err, data) => {
      if (err) return reject(err);
      
      const lines = data.split('\n');
      const customers = lines.map(line => {
        const parts = line.split(/[,	]/).map(p => p.trim());
        return {
          phone: parts[0] || '',
          name: parts[1] || '',
          remark: parts[2] || ''
        };
      }).filter(c => c.phone);
      
      resolve(customers);
    });
  });
};

export const uploadImage = async (req: Request, res: Response) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '未上传图片' });
    }
    
    // 这里应该调用OCR服务识别图片中的文字
    // 暂时返回文件路径，实际使用时需要集成OCR服务
    res.json({
      message: '图片上传成功',
      file_path: req.file.path,
      note: 'OCR识别功能需要集成OCR服务'
    });
  } catch (error) {
    console.error('上传图片错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
