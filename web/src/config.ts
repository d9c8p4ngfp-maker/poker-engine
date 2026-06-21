/**
 * API 基础地址
 *
 * - 本地开发: 留空，走 Vite proxy → localhost:8080
 * - 生产部署: 设置为 Render 后端地址
 *    示例: https://poker-api.onrender.com
 *
 * 在 Vercel 环境变量中设置 VITE_API_BASE_URL 即可。
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? ''
