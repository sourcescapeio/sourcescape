import * as fs from 'fs';
import * as yaml from 'js-yaml';
import * as path from 'path';

export default yaml.load(fs.readFileSync(path.resolve(__dirname, '../../config/local.yaml')).toString()) as any;
