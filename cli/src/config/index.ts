import localConfig from './local';
import remoteConfig from './remote';

// Local here refers to using local Docker images for debug
const useLocal = process.env.LOCAL_IMAGES === "true";

let config: any = null;
if (useLocal) {
  console.warn('USING LOCAL IMAGES');
  config = localConfig;
} else {
  config = remoteConfig;
}

export default config
